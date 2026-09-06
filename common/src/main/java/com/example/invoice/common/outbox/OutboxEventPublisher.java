package com.example.invoice.common.outbox;

/**
 * The delivery leg of the outbox.
 *
 * Publishing is synchronous by contract. The dispatcher marks a row published
 * on return from {@link #publish}, so an implementation that hands off
 * asynchronously would mark rows delivered that never left — quietly
 * reintroducing the loss the outbox exists to prevent.
 *
 * This is the only place transport knowledge lives. Kafka is the default; the
 * consolidated deployment supplies an in-process implementation instead, which
 * is what makes a single-artifact deployment possible without a broker. See
 * docs/adr/003.
 */
public interface OutboxEventPublisher {

    String EVENT_ID_HEADER = "X-Event-Id";
    String EVENT_TYPE_HEADER = "X-Event-Type";

    /**
     * @param eventId service-prefixed and stable across redeliveries;
     *                consumers key their idempotency check on it
     * @param event   the recorded event, payload already serialised
     * @throws Exception caught per event by the dispatcher, which records the
     *                   failure and applies backoff. Deliberately broad: the
     *                   Kafka implementation's timed get throws three checked
     *                   types and the dispatcher treats all failures alike.
     */
    void publish(String eventId, OutboxEvent event) throws Exception;
}