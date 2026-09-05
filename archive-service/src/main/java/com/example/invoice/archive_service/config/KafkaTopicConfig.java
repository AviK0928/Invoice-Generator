package com.example.invoice.archive_service.config;

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
    public NewTopic invoiceDeleteTopic() {
        return TopicBuilder.name(Topics.INVOICE_DELETE).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic unarchiveInvoicesTopic() {
        return TopicBuilder.name(Topics.UNARCHIVE_INVOICES).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic invoiceArchivedDlt() {
        return TopicBuilder.name(Topics.INVOICE_ARCHIVED + Topics.DLT_SUFFIX)
                .partitions(1).replicas(1).build();
    }
}