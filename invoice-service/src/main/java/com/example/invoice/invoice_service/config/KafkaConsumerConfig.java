package com.example.invoice.invoice_service.config;

import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final String TRUSTED_PACKAGES = "com.example.invoice.common.kafka.dto";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    private Map<String, Object> baseConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        return props;
    }

    private <T> ConsumerFactory<String, T> consumerFactory() {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages(TRUSTED_PACKAGES);
        return new DefaultKafkaConsumerFactory<>(
                baseConfig(), new StringDeserializer(), deserializer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
            ConsumerFactory<String, T> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, CustomerEventDTO> customerEventConsumerFactory() {
        return consumerFactory();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerEventDTO> customerEventKafkaListenerFactory() {
        return listenerFactory(customerEventConsumerFactory());
    }

    @Bean
    public ConsumerFactory<String, ArchiveEventDTO> archiveResponseConsumerFactory() {
        return consumerFactory();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ArchiveEventDTO> archiveResponseKafkaListenerFactory() {
        return listenerFactory(archiveResponseConsumerFactory());
    }

    @Bean
    public ConsumerFactory<String, InvoiceEventDTO> invoiceDeletionConsumerFactory() {
        return consumerFactory();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InvoiceEventDTO> invoiceDeletionKafkaListenerFactory() {
        return listenerFactory(invoiceDeletionConsumerFactory());
    }
}