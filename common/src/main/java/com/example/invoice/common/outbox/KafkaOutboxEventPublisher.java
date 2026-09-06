package com.example.invoice.common.outbox;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Lifted verbatim from OutboxDispatcher.publish. Behaviour is unchanged:
 * same record shape, same headers, same five-second blocking send.
 */
@RequiredArgsConstructor
public class KafkaOutboxEventPublisher implements OutboxEventPublisher {

    /** Sits above the producer's own 5s request timeout, not below it. */
    private static final int PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> stringKafkaTemplate;

    @Override
    public void publish(String eventId, OutboxEvent event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(), event.getEventKey(), event.getPayload());

        record.headers().add(EVENT_ID_HEADER,
                eventId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EVENT_TYPE_HEADER,
                event.getEventType().getBytes(StandardCharsets.UTF_8));

        stringKafkaTemplate.send(record).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}