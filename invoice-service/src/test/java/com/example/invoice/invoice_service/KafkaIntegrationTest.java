package com.example.invoice.invoice_service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need a broker as well as a database.
 *
 * Separate from {@link IntegrationTest} because Kafka adds ~20s to startup and
 * only the delivery leg of the outbox needs it — recording is asserted against
 * Postgres alone.
 *
 * Tagged so it can be excluded locally: on a 2-core Codespace with
 * docker-in-docker, the broker is the least reliable part of the suite.
 *
 * ./mvnw verify # everything
 * ./mvnw verify -DexcludedGroups=kafka # Postgres only
 */
@Tag("kafka")
public abstract class KafkaIntegrationTest extends IntegrationTest {

    // kafka-native, not the standard image: it starts in a fraction of the
    // time, which matters on two cores. 3.8.0 rather than 3.9.0 because
    // Testcontainers 1.21.2 generates an invalid advertised.listeners for
    // 3.9.0 and the broker refuses to start with "advertised.listeners cannot
    // use the nonroutable meta-address 0.0.0.0". Compose still runs 3.9.0;
    // only the test container is pinned back.
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @BeforeAll
    static void startKafka() {
        // Not a static initialiser: that runs at class-load during discovery,
        // so the container starts even when these tests are excluded by tag.
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
    }
}