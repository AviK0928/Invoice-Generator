package com.example.invoice.archive_service.config;

import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
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

    @Bean
    public ConsumerFactory<String, ArchiveEventDTO> archiveEventConsumerFactory() {
        JsonDeserializer<ArchiveEventDTO> deserializer = new JsonDeserializer<>(ArchiveEventDTO.class);
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

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ArchiveEventDTO> archiveKafkaListenerContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ArchiveEventDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(archiveEventConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}