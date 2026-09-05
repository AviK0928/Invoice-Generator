package com.example.invoice.invoice_service;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need a real database.
 *
 * Postgres only. Most assertions here are about rows — the invoice, its items,
 * the outbox record — and none of those need a broker. Tests that do need one
 * extend {@link KafkaIntegrationTest} instead, which costs ~20s of container
 * startup.
 *
 * The container is static and started in a static block, so all subclasses
 * share one instance per JVM rather than starting one per test class. Ryuk
 * removes it when the JVM exits.
 *
 * @ServiceConnection wires the datasource automatically; no
 * @DynamicPropertySource needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }
}