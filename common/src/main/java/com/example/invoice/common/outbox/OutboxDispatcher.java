package com.example.invoice.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Delivers recorded events to Kafka.
 *
 * Runs after the domain transaction has committed, so a broker outage delays
 * events rather than failing writes. Delivery is at-least-once: a publish that
 * succeeds but whose mark-published fails will republish. Consumers must be
 * idempotent.
 *
 * Shared by every publishing service. Nothing here is service-specific — the
 * event-id prefix comes from spring.application.name.
 */
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    public static final String EVENT_ID_HEADER = "X-Event-Id";
    public static final String EVENT_TYPE_HEADER = "X-Event-Type";

    /** 2^8 = 256s, capped at 300. Reached by roughly the eighth attempt. */
    private static final int MAX_BACKOFF_EXPONENT = 8;
    private static final long MAX_BACKOFF_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int PUBLISH_TIMEOUT_SECONDS = 5;
    private static final int RETENTION_DAYS = 7;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    @Value("${outbox.batch-size:100}")
    private int batchSize;

    @Value("${outbox.max-attempts:300}")
    private int maxAttempts;

    /** Prefixes the event id, so ids are unique across services. */
    @Value("${spring.application.name}")
    private String serviceName;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    @Transactional
    public void dispatch() {
        List<OutboxEvent> batch = repository.claimBatch(batchSize, maxAttempts);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                publish(event);
            } catch (Exception e) {
                // Caught per event: one unpublishable message must not stall
                // the rest of the batch behind it.
                recordFailure(event, e);
            }
        }
        repository.saveAll(batch);
    }

    /**
     * Synchronous by design. An async send would let the transaction commit
     * before delivery is known, marking rows published that never left.
     */
    private void publish(OutboxEvent event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(), event.getEventKey(), event.getPayload());

        // Stable and unique across services. Consumers key their idempotency
        // check on this — at-least-once delivery means they will see it again.
        record.headers().add(EVENT_ID_HEADER,
                (serviceName + ":" + event.getId()).getBytes(StandardCharsets.UTF_8));
        record.headers().add(EVENT_TYPE_HEADER,
                event.getEventType().getBytes(StandardCharsets.UTF_8));

        stringKafkaTemplate.send(record).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        event.setPublishedAt(LocalDateTime.now());
        event.setLastError(null);
        event.setNextAttemptAt(null);
    }

    private void recordFailure(OutboxEvent event, Exception e) {
        int attempt = event.getAttempts() + 1;
        long backoff = backoffSeconds(attempt);

        event.setAttempts(attempt);
        event.setLastError(truncate(e.getMessage()));
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoff));

        if (attempt >= maxAttempts) {
            log.error("Outbox event {} abandoned after {} attempts (topic={}). "
                    + "Requeue via this service's outbox admin endpoint.",
                    event.getId(), attempt, event.getTopic(), e);
        } else {
            log.warn("Outbox event {} failed (attempt {}/{}), retrying in {}s: {}",
                    event.getId(), attempt, maxAttempts, backoff, e.getMessage());
        }
    }

    /**
     * Exponential, capped at five minutes. Retrying every poll interval for the
     * duration of an outage wastes cycles and hammers a broker that is still
     * coming up.
     */
    private long backoffSeconds(int attempt) {
        return Math.min(MAX_BACKOFF_SECONDS,
                (long) Math.pow(2, Math.min(attempt, MAX_BACKOFF_EXPONENT)));
    }

    /** Published rows are kept briefly for debugging, then removed. */
    @Scheduled(cron = "${outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        int deleted = repository.deletePublishedBefore(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("Removed {} published outbox events older than {} days",
                    deleted, RETENTION_DAYS);
        }
    }

    /**
     * Resets abandoned events so the dispatcher reconsiders them. Backs the
     * admin endpoint — recovery should not require a psql session.
     */
    @Transactional
    public int requeueAbandoned() {
        int requeued = repository.requeueAbandoned(maxAttempts);
        log.info("Requeued {} abandoned outbox events", requeued);
        return requeued;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH)
                : message;
    }
}