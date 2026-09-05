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
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
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

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> targetType) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(targetType);
        deserializer.addTrustedPackages(TRUSTED_PACKAGES);
        // The outbox publishes pre-serialised strings with no __TypeId__
        // header, so the target type must be explicit.
        deserializer.setUseTypeHeaders(false);

        // ErrorHandlingDeserializer is load-bearing: without it a malformed
        // message fails before the listener is reached, the error handler never
        // sees it, and the container retries the same record forever.
        return new DefaultKafkaConsumerFactory<>(baseConfig(),
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(deserializer));
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
            ConsumerFactory<String, T> consumerFactory, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ----------------------------------------------------------- listeners

    @Bean
    public ConsumerFactory<String, CustomerEventDTO> customerEventConsumerFactory() {
        return consumerFactory(CustomerEventDTO.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerEventDTO> customerEventKafkaListenerFactory(
            DefaultErrorHandler errorHandler) {
        return listenerFactory(customerEventConsumerFactory(), errorHandler);
    }

    @Bean
    public ConsumerFactory<String, ArchiveEventDTO> archiveResponseConsumerFactory() {
        return consumerFactory(ArchiveEventDTO.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ArchiveEventDTO> archiveResponseKafkaListenerFactory(
            DefaultErrorHandler errorHandler) {
        return listenerFactory(archiveResponseConsumerFactory(), errorHandler);
    }

    @Bean
    public ConsumerFactory<String, InvoiceEventDTO> invoiceDeletionConsumerFactory() {
        return consumerFactory(InvoiceEventDTO.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InvoiceEventDTO> invoiceDeletionKafkaListenerFactory(
            DefaultErrorHandler errorHandler) {
        return listenerFactory(invoiceDeletionConsumerFactory(), errorHandler);
    }
}