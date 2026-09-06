package com.example.invoice.archive_service;

import com.example.invoice.common.kafka.Topics;
import org.junit.jupiter.api.Tag;
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
 * Same shape as the other services. Every topic this service declares or
 * listens to is listed and auto-create is off, matching the deployed broker.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@EmbeddedKafka(partitions = 1, brokerProperties = "auto.create.topics.enable=false", topics = {
        Topics.INVOICE_ARCHIVED,
        Topics.INVOICE_DELETE,
        Topics.UNARCHIVE_INVOICES,
        Topics.INVOICE_ARCHIVED + Topics.DLT_SUFFIX
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