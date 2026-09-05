package com.example.invoice.invoice_service.service;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.invoice_service.IntegrationTest;
import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.dto.InvoiceResponseDTO;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.exception.InvalidCustomerException;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Recording only: that the event is written in the same transaction as the
 * invoice. Delivery is covered by OutboxDispatcherIT. The dispatcher is not
 * running here — the test profile parks its poll interval an hour out — so
 * these rows stay unpublished for the life of the test.
 */
class InvoiceServiceIT extends IntegrationTest {

    @Autowired
    InvoiceService invoiceService;
    @Autowired
    InvoiceRepository invoiceRepository;
    @Autowired
    LocalCustomerRepository customerRepository;
    @Autowired
    OutboxEventRepository outboxRepository;

    @BeforeEach
    void seed() {
        invoiceRepository.deleteAll();
        outboxRepository.deleteAll();
        customerRepository.deleteAll();
        customerRepository.save(LocalCustomer.builder()
                .customerId(1L).name("Test Co").email("test@example.com").build());
    }

    @Test
    @DisplayName("creating an invoice persists it with a content hash")
    void createPersistsWithContentHash() {
        InvoiceResponseDTO created = invoiceService.createInvoice(request());

        assertThat(created.getInvoiceId()).isNotNull();
        assertThat(created.getTotalAmount()).isEqualByComparingTo("20.00");

        // contentHash is nullable=false and the mapper never set it, so this
        // whole path threw a constraint violation before Phase 1.
        assertThat(invoiceRepository.findById(created.getInvoiceId()))
                .get()
                .extracting(i -> i.getContentHash())
                .asString().hasSize(64);
    }

    @Test
    @DisplayName("creating the same invoice twice returns the same invoice")
    void createIsIdempotent() {
        InvoiceResponseDTO first = invoiceService.createInvoice(request());
        InvoiceResponseDTO second = invoiceService.createInvoice(request());

        assertThat(second.getInvoiceId()).isEqualTo(first.getInvoiceId());
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the idempotent path records no second event")
    void idempotentCreateDoesNotDuplicateEvents() {
        invoiceService.createInvoice(request());
        invoiceService.createInvoice(request());

        // Known gap, documented in ADR 004: a consumer that missed the original
        // cannot recover it by retrying the create. Asserted so the behaviour
        // is deliberate rather than accidental.
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("creation records an event in the same transaction")
    void createRecordsAnOutboxEvent() {
        InvoiceResponseDTO created = invoiceService.createInvoice(request());

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getTopic()).isEqualTo("invoice-events");
                    assertThat(e.getAggregateId()).isEqualTo(created.getInvoiceId().toString());
                    assertThat(e.getPayload()).contains("test@example.com");
                });
    }

    @Test
    @DisplayName("an unknown customer is rejected before anything is written")
    void unknownCustomerIsRejected() {
        InvoiceRequestDTO dto = request();
        dto.setCustomerId(9999L);

        assertThatThrownBy(() -> invoiceService.createInvoice(dto))
                .isInstanceOf(InvalidCustomerException.class);

        assertThat(invoiceRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    // ------------------------------------------------------------- fixtures

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