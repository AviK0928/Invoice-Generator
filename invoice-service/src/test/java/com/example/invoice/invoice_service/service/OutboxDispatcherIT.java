package com.example.invoice.invoice_service.service;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.invoice_service.KafkaIntegrationTest;
import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxDispatcherIT extends KafkaIntegrationTest {

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
    @EnabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = """
            The Kafka container starts but is unreachable from inside
            docker-in-docker: KafkaAdmin times out fetching topics and
            the producer cannot resolve invoice-events in metadata.
            GitHub Actions runners have a plain Docker daemon, where
            this works.
            """)
    @DisplayName("the dispatcher publishes recorded events")
    void dispatcherPublishesEvents() {
        invoiceService.createInvoice(request());

        assertThat(outboxRepository.countByPublishedAtIsNull()).isOne();

        // The dispatcher polls every 200ms under the test profile.
        await().atMost(ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxRepository.countByPublishedAtIsNull()).isZero());
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