package com.example.invoice.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Claims recorded events and hands them to a publisher.
 *
 * Runs after the domain transaction has committed, so a transport outage
 * delays events rather than failing writes. Delivery is at-least-once: a
 * publish that succeeds but whose mark-published fails will republish.
 * Consumers must be idempotent.
 *
 * What is delivered over is not this class's concern — see
 * {@link OutboxEventPublisher}. Kafka is the default; the consolidated
 * deployment substitutes an in-process publisher and everything here is
 * unchanged.
 *
 * Shared by every publishing service. Nothing here is service-specific — the
 * event-id prefix comes from spring.application.name.
 */
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    /** 2^8 = 256s, capped at 300. Reached by roughly the eighth attempt. */
    private static final int MAX_BACKOFF_EXPONENT = 8;
    private static final long MAX_BACKOFF_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int RETENTION_DAYS = 7;

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;

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
     * The publisher returns only once delivery is known — that contract is
     * what makes marking the row here safe. See {@link OutboxEventPublisher}.
     */
    private void publish(OutboxEvent event) throws Exception {
        // Stable and unique across services. Consumers key their idempotency
        // check on this — at-least-once delivery means they will see it again.
        publisher.publish(serviceName + ":" + event.getId(), event);

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