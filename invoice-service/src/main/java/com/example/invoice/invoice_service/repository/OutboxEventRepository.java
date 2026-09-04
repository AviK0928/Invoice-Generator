package com.example.invoice.invoice_service.repository;

import com.example.invoice.invoice_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * FOR UPDATE SKIP LOCKED lives in the SQL rather than in a @Lock
     * annotation — Spring Data applies LockModeType through JPQL and cannot
     * apply it to a native query, which throws at execution. Skip-locked lets
     * multiple instances dispatch concurrently, each claiming a distinct batch
     * instead of blocking on one another.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
              AND attempts < :maxAttempts
              AND (next_attempt_at IS NULL OR next_attempt_at <= now())
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(int batchSize, int maxAttempts);

    long countByPublishedAtIsNull();

    long countByAttemptsGreaterThanEqualAndPublishedAtIsNull(int maxAttempts);

    /** Requeues abandoned events. Backs the admin replay endpoint. */
    @Modifying
    @Query("""
            UPDATE OutboxEvent o
               SET o.attempts = 0, o.nextAttemptAt = null, o.lastError = null
             WHERE o.publishedAt IS NULL AND o.attempts >= :maxAttempts
            """)
    int requeueAbandoned(int maxAttempts);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.publishedAt IS NOT NULL AND o.publishedAt < :before")
    int deletePublishedBefore(LocalDateTime before);
}