package com.example.invoice.export_service;

import com.example.invoice.common.kafka.Topics;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests: one Postgres container, one in-process broker.
 *
 * Same shape as invoice-service and import-service. No test here publishes or
 * consumes over the wire — the handler service is called directly — but
 * KafkaTopicConfig declares NewTopic beans and the @KafkaListener needs a
 * broker to subscribe to, so one has to exist or every context startup logs a
 * "Could not configure topics" stack trace.
 *
 * @AutoConfigureMockMvc is for the PDF download endpoint. It sits here rather
 *                       than on that test class so every subclass produces the
 *                       same MergedContextConfiguration and Spring caches a
 *                       single context; it is inert for the tests that call
 *                       services directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
@EmbeddedKafka(partitions = 1, brokerProperties = "auto.create.topics.enable=false", topics = {
        Topics.INVOICE_EVENTS,
        Topics.INVOICE_EVENTS + Topics.DLT_SUFFIX,
        Topics.INVOICE_PDF_REQUESTED,
        Topics.INVOICE_PDF_REQUESTED + Topics.DLT_SUFFIX,
        Topics.INVOICE_PDF_READY
})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }
}