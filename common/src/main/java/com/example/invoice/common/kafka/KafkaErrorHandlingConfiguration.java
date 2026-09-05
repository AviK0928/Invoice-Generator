package com.example.invoice.common.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Retry-then-dead-letter for every consumer.
 *
 * Imported only by services that consume. Without it a failed event is retried
 * briefly, the offset is committed, and the message is gone with no record it
 * ever failed.
 */
@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final int MAX_RETRIES = 5;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> dltKafkaTemplate) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(dltKafkaTemplate), backOff);
        handler.setLogLevel(KafkaException.Level.ERROR);
        return handler;
    }

    /**
     * Delegates serialization by type on both key and value: a record that
     * failed deserialization arrives as raw bytes and must be republished
     * verbatim, while one that deserialized but failed processing is JSON. A
     * plain StringSerializer would fail on the former — losing the poison
     * message you were trying to preserve.
     */
    @Bean
    public KafkaTemplate<Object, Object> dltKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(config,
                new DelegatingByTypeSerializer(Map.of(
                        byte[].class, new ByteArraySerializer(),
                        Object.class, new StringSerializer())),
                new DelegatingByTypeSerializer(Map.of(
                        byte[].class, new ByteArraySerializer(),
                        Object.class, new JsonSerializer<>())));

        return new KafkaTemplate<>(factory);
    }
}