package com.example.invoice.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Param on every named parameter is required, not stylistic. This module has
 *        no spring-boot-starter-parent and therefore no -parameters compiler
 *        flag, so
 *        argument names are absent from the bytecode and Spring Data cannot
 *        bind them.
 *        The failure is at runtime, not compile time: "For queries with named
 *        parameters you need to provide names for method parameters".
 */
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
        List<OutboxEvent> claimBatch(@Param("batchSize") int batchSize,
                        @Param("maxAttempts") int maxAttempts);

        long countByPublishedAtIsNull();

        long countByAttemptsGreaterThanEqualAndPublishedAtIsNull(int maxAttempts);

        /** Requeues abandoned events. Backs the admin replay endpoint. */
        @Modifying
        @Query("""
                        UPDATE OutboxEvent o
                           SET o.attempts = 0, o.nextAttemptAt = null, o.lastError = null
                         WHERE o.publishedAt IS NULL AND o.attempts >= :maxAttempts
                        """)
        int requeueAbandoned(@Param("maxAttempts") int maxAttempts);

        @Modifying
        @Query("DELETE FROM OutboxEvent o WHERE o.publishedAt IS NOT NULL AND o.publishedAt < :before")
        int deletePublishedBefore(@Param("before") LocalDateTime before);
}