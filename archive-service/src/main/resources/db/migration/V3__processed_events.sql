-- Inbox. At-least-once delivery means a consumer will see the same event more
-- than once — after a rebalance, a redelivery, or an outbox republish where the
-- send succeeded but the mark-published failed.
--
-- Deliberately NOT shared via the common module: each service owns its schema,
-- and a shared entity would couple them.

CREATE TABLE processed_events (
    event_id     VARCHAR(128) PRIMARY KEY,
    event_type   VARCHAR(64),
    processed_at TIMESTAMP(6) NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);