package com.example.invoice.invoice_service.config;

import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, CustomerEventDTO> customerEventConsumerFactory() {
        JsonDeserializer<CustomerEventDTO> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.example.invoice.common.kafka.dto");
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "invoice-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerEventDTO> customerEventKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CustomerEventDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(customerEventConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, ArchiveEventDTO> archiveResponseConsumerFactory() {
        JsonDeserializer<ArchiveEventDTO> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.example.invoice.common.kafka.dto");
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "invoice-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ArchiveEventDTO> archiveResponseKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ArchiveEventDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(archiveResponseConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, InvoiceEventDTO> invoiceDeletionConsumerFactory() {
        JsonDeserializer<InvoiceEventDTO> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.example.invoice.common.kafka.dto");
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "invoice-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InvoiceEventDTO> invoiceDeletionKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InvoiceEventDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(invoiceDeletionConsumerFactory());
        return factory;
    }

//    @Bean
//    public ConsumerFactory<String, InvoiceEventDTO> invoiceImportedConsumerFactory() {
//        JsonDeserializer<InvoiceEventDTO> deserializer = new JsonDeserializer<>(InvoiceEventDTO.class);
//        deserializer.addTrustedPackages("com.example.invoice.common.kafka.dto");
//
//        Map<String, Object> props = new HashMap<>();
//        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, "invoice-service-group");
//        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
//
//        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
//    }
//
//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, InvoiceEventDTO> invoiceImportedKafkaListenerContainerFactory() {
//        ConcurrentKafkaListenerContainerFactory<String, InvoiceEventDTO> factory =
//                new ConcurrentKafkaListenerContainerFactory<>();
//        factory.setConsumerFactory(invoiceImportedConsumerFactory());
//        return factory;
//    }

}