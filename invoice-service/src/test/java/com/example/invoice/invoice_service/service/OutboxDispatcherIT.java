package com.example.invoice.invoice_service.service;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.invoice_service.IntegrationTest;
import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The delivery leg of the outbox. Recording is covered by InvoiceServiceIT,
 * which needs no broker.
 *
 * Uses @EmbeddedKafka rather than a Testcontainers broker. Testcontainers
 * 1.21.2 reports apache/kafka-native as started in under half a second — its
 * wait strategy passes before the broker is listening, so KafkaAdmin times out
 * fetching topics and the producer never resolves metadata. That happens on a
 * plain GitHub Actions daemon too, so it is not a docker-in-docker quirk.
 *
 * The embedded broker runs in-process: no image, no wait strategy, and it works
 * identically everywhere.
 */
@EmbeddedKafka(partitions = 1, topics = { Topics.INVOICE_EVENTS, Topics.INVOICE_ARCHIVED })
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OutboxDispatcherIT extends IntegrationTest {

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
    @DisplayName("the dispatcher publishes recorded events")
    void dispatcherPublishesEvents() {
        invoiceService.createInvoice(request());

        // Recorded but not yet sent — the dispatcher runs on a schedule.
        assertThat(outboxRepository.countByPublishedAtIsNull()).isOne();

        // Polls every 200ms under the test profile.
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