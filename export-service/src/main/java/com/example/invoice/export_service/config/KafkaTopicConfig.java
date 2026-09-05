package com.example.invoice.export_service.config;

import com.example.invoice.common.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * Created by DeadLetterPublishingRecoverer otherwise; declared so it
     * exists before the first failure and auto-create can stay off.
     */
    @Bean
    public NewTopic invoiceEventsDlt() {
        return TopicBuilder.name(Topics.INVOICE_EVENTS + Topics.DLT_SUFFIX)
                .partitions(1).replicas(1).build();
    }
}