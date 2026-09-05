package com.example.invoice.invoice_service.config;

import com.example.invoice.common.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declaring topics lets auto-create be disabled on the broker, so a typo in a
 * topic name fails loudly instead of silently creating a new one.
 *
 * Single partition and replica: this is a single-broker deployment, and
 * ordering per aggregate matters more than parallelism at this volume.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic invoiceEventsTopic() {
        return TopicBuilder.name(Topics.INVOICE_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic invoiceArchivedTopic() {
        return TopicBuilder.name(Topics.INVOICE_ARCHIVED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic customerEventsDlt() {
        return TopicBuilder.name(Topics.CUSTOMER_EVENTS + Topics.DLT_SUFFIX)
                .partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic invoiceDeleteDlt() {
        return TopicBuilder.name(Topics.INVOICE_DELETE + Topics.DLT_SUFFIX)
                .partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic unarchiveInvoicesDlt() {
        return TopicBuilder.name(Topics.UNARCHIVE_INVOICES + Topics.DLT_SUFFIX)
                .partitions(1).replicas(1).build();
    }
}