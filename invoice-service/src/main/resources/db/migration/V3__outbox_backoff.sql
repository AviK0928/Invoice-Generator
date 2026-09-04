-- Exponential backoff for outbox delivery.
--
-- Without this the dispatcher retries every poll interval for the whole
-- duration of an outage, hammering a recovering broker. next_attempt_at lets a
-- failed event sit out an increasing delay before being reconsidered.

ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMP(6);

DROP INDEX idx_outbox_unpublished;

-- Covers the dispatcher's full predicate: unpublished, and due.
CREATE INDEX idx_outbox_pending
    ON outbox_events (next_attempt_at, id)
    WHERE published_at IS NULL;