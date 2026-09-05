package com.example.invoice.invoice_service;

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
 * Postgres is a container because the schema under test has to be the one
 * Flyway ships. Kafka is {@code @EmbeddedKafka} rather than a container because
 * the container does not work: Testcontainers 1.21.2 reports
 * {@code apache/kafka-native:3.8.0} as started in under half a second — the
 * wait strategy passes before the broker is listening, so KafkaAdmin times out
 * fetching topics and the producer never resolves metadata. That reproduces on
 * a plain GitHub Actions daemon, so it was never a docker-in-docker quirk.
 *
 * Both live here rather than on the tests that need them so every subclass
 * produces the same MergedContextConfiguration and Spring caches one context
 * for the whole run. Declaring the broker only on the test that publishes cost
 * a second context, a second schema load, and a five-second KafkaAdmin timeout
 * on every broker-less class.
 *
 * Every topic the service touches is declared and auto-create is off, matching
 * the deployed broker: a typo in a topic name has to fail here for the same
 * reason it fails in production.
 *
 * The container is static and started in a static block, so all subclasses
 * share one instance per JVM. Ryuk removes it when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@EmbeddedKafka(partitions = 1, brokerProperties = "auto.create.topics.enable=false", topics = {
        Topics.CUSTOMER_EVENTS,
        Topics.INVOICE_EVENTS,
        Topics.INVOICE_ARCHIVED,
        Topics.INVOICE_DELETE,
        Topics.UNARCHIVE_INVOICES,
        Topics.CUSTOMER_EVENTS + Topics.DLT_SUFFIX,
        Topics.INVOICE_DELETE + Topics.DLT_SUFFIX,
        Topics.UNARCHIVE_INVOICES + Topics.DLT_SUFFIX
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