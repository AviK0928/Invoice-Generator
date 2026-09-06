package com.example.invoice.invoice_service.service;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.invoice_service.IntegrationTest;
import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.entity.PdfRequest;
import com.example.invoice.invoice_service.enums.PdfRequestStatus;
import com.example.invoice.invoice_service.exception.InvoiceNotFoundException;
import com.example.invoice.invoice_service.exception.PdfRequestNotFoundException;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import com.example.invoice.invoice_service.repository.PdfRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The request half of asynchronous PDF generation. The generating half lives in
 * export-service; the two meet over invoice-pdf-requested.
 *
 * The point of these is that the request row and the event commit together. A
 * direct Kafka send after the save could leave a request nothing will ever act
 * on, stuck PENDING and indistinguishable from one still in flight.
 */
class PdfRequestServiceIT extends IntegrationTest {

    @Autowired
    InvoiceService invoiceService;
    @Autowired
    PdfRequestService pdfRequestService;
    @Autowired
    PdfReadyHandlerService pdfReadyHandlerService;
    @Autowired
    InvoiceRepository invoiceRepository;
    @Autowired
    LocalCustomerRepository customerRepository;
    @Autowired
    PdfRequestRepository pdfRequestRepository;
    @Autowired
    OutboxEventRepository outboxRepository;

    private Long invoiceId;

    @BeforeEach
    void seed() {
        pdfRequestRepository.deleteAll();
        invoiceRepository.deleteAll();
        outboxRepository.deleteAll();
        customerRepository.deleteAll();
        customerRepository.save(LocalCustomer.builder()
                .customerId(1L).name("Test Co").email("test@example.com").build());

        invoiceId = invoiceService.createInvoice(request()).getInvoiceId();
        // The create path records its own event; clear it so the assertions
        // below are about the PDF request only.
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("a request is recorded PENDING with an event in the same transaction")
    void requestRecordsRowAndEvent() {
        PdfRequest request = pdfRequestService.request(invoiceId);

        assertThat(request.getStatus()).isEqualTo(PdfRequestStatus.PENDING);
        assertThat(request.getCompletedAt()).isNull();
        assertThat(pdfRequestRepository.findById(request.getRequestId())).isPresent();

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getTopic()).isEqualTo(Topics.INVOICE_PDF_REQUESTED);
                    assertThat(e.getEventType()).isEqualTo("PDF_REQUESTED");
                    // Keyed on the request, not the invoice: two requests for
                    // one invoice are two independent pieces of work.
                    assertThat(e.getAggregateId()).isEqualTo(request.getRequestId().toString());
                    assertThat(e.getPayload()).contains(request.getRequestId().toString());
                    assertThat(e.getPublishedAt()).isNull();
                });
    }

    @Test
    @DisplayName("an unknown invoice is rejected before anything is written")
    void unknownInvoiceIsRejected() {
        assertThatThrownBy(() -> pdfRequestService.request(9999L))
                .isInstanceOf(InvoiceNotFoundException.class);

        // No orphan request and no event for work that cannot be done.
        assertThat(pdfRequestRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("the ready event flips the request to READY")
    void readyEventCompletesTheRequest() {
        PdfRequest request = pdfRequestService.request(invoiceId);

        pdfReadyHandlerService.handle(PdfRequestEventDTO.builder()
                .requestId(request.getRequestId().toString())
                .invoiceId(invoiceId)
                .build(), "export-service:1");

        PdfRequest reloaded = pdfRequestService.status(request.getRequestId());
        assertThat(reloaded.getStatus()).isEqualTo(PdfRequestStatus.READY);
        assertThat(reloaded.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("a redelivered ready event is skipped by the inbox")
    void readyEventIsIdempotent() {
        PdfRequest request = pdfRequestService.request(invoiceId);
        PdfRequestEventDTO event = PdfRequestEventDTO.builder()
                .requestId(request.getRequestId().toString())
                .invoiceId(invoiceId)
                .build();

        pdfReadyHandlerService.handle(event, "export-service:1");
        var firstCompletion = pdfRequestService.status(request.getRequestId()).getCompletedAt();

        pdfReadyHandlerService.handle(event, "export-service:1");

        // At-least-once delivery means this arrives twice. The second must not
        // move completedAt, which is what a client would see as the PDF being
        // regenerated.
        assertThat(pdfRequestService.status(request.getRequestId()).getCompletedAt())
                .isEqualTo(firstCompletion);
    }

    @Test
    @DisplayName("a ready event for an unknown request is ignored, not dead-lettered")
    void readyEventForUnknownRequestIsIgnored() {
        // The request row is deleted with its invoice, so this is a normal
        // outcome rather than a fault. Throwing here would raise an alert for
        // something working as intended.
        pdfReadyHandlerService.handle(PdfRequestEventDTO.builder()
                .requestId(UUID.randomUUID().toString())
                .invoiceId(invoiceId)
                .build(), "export-service:2");

        assertThat(pdfRequestRepository.count()).isZero();
    }

    @Test
    @DisplayName("an unknown request id is a domain miss")
    void unknownRequestIdIsRejected() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> pdfRequestService.status(unknown))
                .isInstanceOf(PdfRequestNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    private InvoiceRequestDTO request() {
        InvoiceItemDTO item = new InvoiceItemDTO();
        item.setDescription("Widget");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10.00"));

        InvoiceRequestDTO dto = new InvoiceRequestDTO();
        dto.setCustomerId(1L);
        dto.setInvoiceDate(LocalDate.of(2026, 9, 5));
        dto.setPaymentStatus(PaymentStatus.PENDING);
        dto.setItems(List.of(item));
        return dto;
    }
}