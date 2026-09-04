package com.example.invoice.invoice_service.health;

import com.example.invoice.invoice_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * A backlog is the outbox's silent failure mode: the API keeps working while
 * nothing reaches downstream. Surfacing it here makes it visible without
 * reading the table.
 */
@Component("outbox")
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxEventRepository repository;

    @Value("${outbox.max-attempts:10}")
    private int maxAttempts;

    @Value("${outbox.pending-warn-threshold:1000}")
    private long warnThreshold;

    @Override
    public Health health() {
        long pending = repository.countByPublishedAtIsNull();
        long abandoned = repository.countByAttemptsGreaterThanEqualAndPublishedAtIsNull(maxAttempts);

        Health.Builder builder = (abandoned > 0 || pending > warnThreshold)
                ? Health.down()
                : Health.up();

        return builder
                .withDetail("pending", pending)
                .withDetail("abandoned", abandoned)
                .build();
    }
}