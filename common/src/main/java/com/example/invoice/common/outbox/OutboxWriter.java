package com.example.invoice.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * Records an event for later delivery.
 *
 * Must be called inside the same transaction as the domain write — that is the
 * entire point. The event and the state it describes commit together or not at
 * all.
 */
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void record(String aggregateType, Object aggregateId,
            String topic, String eventType, Object payload) {
        try {
            repository.save(OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(String.valueOf(aggregateId))
                    .topic(topic)
                    .eventKey(String.valueOf(aggregateId))
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payload))
                    .createdAt(LocalDateTime.now())
                    .attempts(0)
                    .build());
        } catch (JsonProcessingException e) {
            // Serialisation failure is a programming error, not a transient
            // one. Fail the transaction rather than record an unusable event.
            throw new IllegalStateException("Could not serialise event payload", e);
        }
    }
}