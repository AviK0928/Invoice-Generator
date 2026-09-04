package com.example.invoice.invoice_service.config;

import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return config;
    }

    @Bean
    public ProducerFactory<String, InvoiceEventDTO> invoiceEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseConfig());
    }

    @Bean
    public KafkaTemplate<String, InvoiceEventDTO> invoiceEventKafkaTemplate() {
        return new KafkaTemplate<>(invoiceEventProducerFactory());
    }

    @Bean
    public ProducerFactory<String, ArchiveEventDTO> archiveTriggerProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseConfig());
    }

    @Bean
    public KafkaTemplate<String, ArchiveEventDTO> archiveTriggerKafkaTemplate() {
        return new KafkaTemplate<>(archiveTriggerProducerFactory());
    }
}