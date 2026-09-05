package com.example.invoice.import_service.config;

import com.example.invoice.common.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declared so auto-create can stay off on the broker: a typo in a topic name
 * then fails loudly instead of silently creating a new one.
 */
@Configuration
public class KafkaTopicConfig {

    /**
     * Nothing consumes this. The only listener was InvoiceImportedConsumer,
     * which was entirely commented out and deleted in Phase 0. Declared so the
     * publish succeeds; see the engineering log.
     */
    @Bean
    public NewTopic invoiceImportedTopic() {
        return TopicBuilder.name(Topics.INVOICE_IMPORTED).partitions(1).replicas(1).build();
    }
}