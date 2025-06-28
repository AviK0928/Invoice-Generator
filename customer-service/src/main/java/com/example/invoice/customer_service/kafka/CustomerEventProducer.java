package com.example.invoice.customer_service.kafka;

import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerEventProducer {

    private final KafkaTemplate<String, CustomerEventDTO> kafkaTemplate;
    private static final String TOPIC = "customer-events";

    public void publish(CustomerEventDTO eventDTO) {
        kafkaTemplate.send(TOPIC, eventDTO.getCustomerId().toString(), eventDTO);
    }
}