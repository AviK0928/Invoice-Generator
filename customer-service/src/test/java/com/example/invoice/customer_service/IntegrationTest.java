package com.example.invoice.customer_service;

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
 * Publish-only service, so one topic and no DLT — nothing here consumes. The
 * broker exists because KafkaTopicConfig declares a NewTopic and KafkaAdmin
 * reconciles it at startup whether or not a test publishes.
 *
 * Separate from CustomerControllerErrorContractTest, which is a @WebMvcTest
 * slice with no database and stays that way: it tests the error contract, not
 * persistence, and adding a container to it would cost a minute for nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@EmbeddedKafka(partitions = 1, brokerProperties = "auto.create.topics.enable=false", topics = Topics.CUSTOMER_EVENTS)
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }
}