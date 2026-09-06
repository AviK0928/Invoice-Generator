package com.example.invoice.invoice_service.service;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.outbox.OutboxDispatcher;
import com.example.invoice.common.outbox.OutboxEventPublisher;
import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.invoice_service.IntegrationTest;
import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.dto.InvoiceResponseDTO;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The delivery leg of the outbox: a recorded event reaches the broker with the
 * headers consumers key their idempotency on, and the row is then marked
 * published. Recording is covered by InvoiceServiceIT.
 *
 * dispatch() is called directly rather than waited for. The scheduler is
 * parked in the test profile because a background dispatcher polling every
 * 200ms shares a context and a database with every other test in the run and
 * races their assertions — the "recorded but not yet sent" assertion below is
 * exactly the kind it wins. That @Scheduled fires is Spring's job to get right;
 * what is worth pinning is what dispatch() does when it runs.
 */
class OutboxDispatcherIT extends IntegrationTest {

    private static final int RECORD_TIMEOUT_SECONDS = 10;

    @Autowired
    InvoiceService invoiceService;
    @Autowired
    InvoiceRepository invoiceRepository;
    @Autowired
    LocalCustomerRepository customerRepository;
    @Autowired
    OutboxEventRepository outboxRepository;
    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    EmbeddedKafkaBroker broker;

    /**
     * Diagnostic: this is the address the producer and KafkaAdmin resolve. It
     * must be the broker this test consumes from, or the two are talking to
     * different brokers.
     */
    @Value("${spring.kafka.bootstrap-servers}")
    String configuredBootstrapServers;

    private Consumer<String, String> consumer;

    @BeforeEach
    void seed() {
        invoiceRepository.deleteAll();
        outboxRepository.deleteAll();
        customerRepository.deleteAll();
        customerRepository.save(LocalCustomer.builder()
                .customerId(1L).name("Test Co").email("test@example.com").build());

        // Subscribed from the start of the topic. Records from earlier tests
        // are still there — the database is truncated between tests, the topic
        // is not — so the assertion below matches on this invoice's key rather
        // than taking whatever arrives first.
        consumer = consumer();
        broker.consumeFromAnEmbeddedTopic(consumer, Topics.INVOICE_EVENTS);
    }

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("the dispatcher publishes recorded events and marks them sent")
    void dispatcherPublishesEvents() {
        // Precondition for everything below: the producer publishes to the
        // broker this test consumes from. If these diverge the test can pass
        // while KafkaAdmin times out against a second broker.
        assertThat(configuredBootstrapServers)
                .as("producer bootstrap address vs the broker under test")
                .isEqualTo(broker.getBrokersAsString());

        InvoiceResponseDTO created = invoiceService.createInvoice(request());

        // Recorded by the domain transaction, not yet delivered.
        assertThat(outboxRepository.countByPublishedAtIsNull()).isOne();

        dispatcher.dispatch();

        // The key is the aggregate id, so every event for one invoice lands on
        // the same partition and consumers see them in order.
        ConsumerRecord<String, String> record = awaitRecord(created.getInvoiceId().toString());

        assertThat(record.value()).contains("test@example.com");

        // Consumers key their idempotency check on the event id, which
        // at-least-once delivery makes them need.
        assertThat(header(record, OutboxEventPublisher.EVENT_ID_HEADER))
                .startsWith("invoice-service:");
        assertThat(header(record, OutboxEventPublisher.EVENT_TYPE_HEADER))
                .isEqualTo("CREATED");

        // Marked published only after the broker acknowledged it. The reverse
        // order would let a row be marked delivered that never left.
        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }

    // ------------------------------------------------------------- helpers

    /**
     * Polls to a deadline rather than once: the first poll after a subscription
     * routinely returns empty while the group finishes joining.
     */
    private ConsumerRecord<String, String> awaitRecord(String key) {
        long deadline = System.currentTimeMillis() + RECORD_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(ofSeconds(1));
            for (ConsumerRecord<String, String> record : records.records(Topics.INVOICE_EVENTS)) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key " + key + " arrived on "
                + Topics.INVOICE_EVENTS + " within " + RECORD_TIMEOUT_SECONDS + "s");
    }

    private Consumer<String, String> consumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        // Unique per run: a fixed group would carry committed offsets between
        // test classes sharing this context.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-dispatcher-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).as("header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
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