package com.example.invoice.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Declares the outbox beans for any service that imports this.
 *
 * The code is shared; the schema is not. Each service runs its own migration
 * creating its own outbox_events table in its own database. OutboxEvent is
 * private plumbing with no business meaning, and no service reads another's
 * rows — unlike the event DTOs, where a change alters a contract between
 * services.
 *
 * Entity and repository scanning stays on each service's application class:
 * 
 * @EntityScan replaces the global default rather than adding to it, so it has
 *             to name the service's own package too.
 */
@Configuration
public class OutboxConfiguration {

    @Bean
    public OutboxWriter outboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        return new OutboxWriter(repository, objectMapper);
    }

    @Bean
    public OutboxDispatcher outboxDispatcher(OutboxEventRepository repository,
            KafkaTemplate<String, String> stringKafkaTemplate) {
        return new OutboxDispatcher(repository, stringKafkaTemplate);
    }
}