package com.example.invoice.import_service;

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
 * Same shape as invoice-service's. No test here publishes — the importer only
 * records outbox rows and the dispatcher is parked — but KafkaTopicConfig
 * declares a NewTopic bean, so KafkaAdmin reconciles it at startup regardless.
 * Without a broker that logs a "Could not configure topics" stack trace on
 * every context startup, indistinguishable from a real failure.
 *
 * Both annotations live here so every subclass produces the same
 * MergedContextConfiguration and Spring caches one context for the run.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@EmbeddedKafka(partitions = 1, brokerProperties = "auto.create.topics.enable=false", topics = Topics.INVOICE_IMPORTED)
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }
}