package com.example.invoice.customer_service.config;

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

    @Bean
    public NewTopic customerEventsTopic() {
        return TopicBuilder.name(Topics.CUSTOMER_EVENTS).partitions(1).replicas(1).build();
    }
}