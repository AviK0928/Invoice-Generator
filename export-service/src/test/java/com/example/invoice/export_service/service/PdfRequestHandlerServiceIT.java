package com.example.invoice.export_service.service;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.inbox.ProcessedEventRepository;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.export_service.IntegrationTest;
import com.example.invoice.export_service.entity.ExportCustomer;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import com.example.invoice.export_service.repository.ExportCustomerRepository;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import com.example.invoice.export_service.repository.PdfDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The generating half of asynchronous PDF generation.
 *
 * The invoice comes from this service's own read model, not from the event: the
 * projection is already maintained from invoice-events, so a snapshot in the
 * payload would duplicate data that could disagree with it. The event carries
 * only a request id and an invoice id, and these tests are what hold that
 * design in place.
 */
class PdfRequestHandlerServiceIT extends IntegrationTest {

    private static final Long INVOICE_ID = 1001L;

    @Autowired
    PdfRequestHandlerService handler;
    @Autowired
    ExportInvoiceRepository invoiceRepository;
    @Autowired
    ExportInvoiceItemRepository itemRepository;
    @Autowired
    ExportCustomerRepository customerRepository;
    @Autowired
    PdfDocumentRepository pdfDocumentRepository;
    @Autowired
    OutboxEventRepository outboxRepository;
    @Autowired
    ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void seed() {
        pdfDocumentRepository.deleteAll();
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
        itemRepository.deleteAll();
        invoiceRepository.deleteAll();
        customerRepository.deleteAll();

        customerRepository.save(ExportCustomer.builder()
                .customerId(1L).name("Test Co").email("test@example.com").build());

        ExportInvoice invoice = invoiceRepository.save(ExportInvoice.builder()
                .invoiceId(INVOICE_ID)
                .customerId(1L)
                .invoiceDate(LocalDate.of(2026, 9, 10))
                .totalAmount(new BigDecimal("20.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .archived(false)
                .build());

        itemRepository.save(ExportInvoiceItem.builder()
                .description("Widget")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .totalPrice(new BigDecimal("20.00"))
                .invoice(invoice)
                .build());
    }

    @Test
    @DisplayName("the document and the ready event are written together")
    void storesDocumentAndRecordsReadyEvent() {
        UUID requestId = UUID.randomUUID();

        handler.handle(event(requestId, INVOICE_ID), "invoice-service:1");

        var document = pdfDocumentRepository.findById(requestId).orElseThrow();
        assertThat(document.getInvoiceId()).isEqualTo(INVOICE_ID);
        // A real PDF, not an empty array. A length assertion alone would pass
        // against a renderer that produced nothing.
        assertThat(new String(document.getContent(), 0, 5, StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");

        // Same transaction as the document. Announcing a PDF that was never
        // stored would leave invoice-service reporting READY for a download
        // that 404s.
        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getTopic()).isEqualTo(Topics.INVOICE_PDF_READY);
                    assertThat(e.getEventType()).isEqualTo("PDF_READY");
                    assertThat(e.getAggregateId()).isEqualTo(requestId.toString());
                    assertThat(e.getPublishedAt()).isNull();
                });
    }

    @Test
    @DisplayName("a redelivered request is skipped by the inbox")
    void redeliveryIsSkipped() {
        UUID requestId = UUID.randomUUID();

        handler.handle(event(requestId, INVOICE_ID), "invoice-service:1");
        handler.handle(event(requestId, INVOICE_ID), "invoice-service:1");

        // Without the inbox check the second call would fail on the primary
        // key, dead-letter, and alert on a redelivery that is normal.
        assertThat(pdfDocumentRepository.count()).isOne();
        assertThat(outboxRepository.count()).isOne();
    }

    @Test
    @DisplayName("a request for an unprojected invoice fails rather than storing nothing")
    void unprojectedInvoiceFails() {
        UUID requestId = UUID.randomUUID();

        // The request event overtook the invoice event. Throwing dead-letters
        // the record, which is the honest outcome: retrying later is exactly
        // what should happen.
        assertThatThrownBy(() -> handler.handle(event(requestId, 9999L), "invoice-service:2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("9999");

        assertThat(pdfDocumentRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
        // Not marked processed: a retry must be allowed to succeed.
        assertThat(processedEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("an event with no id is processed without a dedup check")
    void missingEventIdStillProcesses() {
        handler.handle(event(UUID.randomUUID(), INVOICE_ID), null);

        assertThat(pdfDocumentRepository.count()).isOne();
        assertThat(processedEventRepository.count()).isZero();
    }

    private PdfRequestEventDTO event(UUID requestId, Long invoiceId) {
        return PdfRequestEventDTO.builder()
                .requestId(requestId.toString())
                .invoiceId(invoiceId)
                .build();
    }
}