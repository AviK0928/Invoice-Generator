package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.enums.EventType;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerEventConsumer {

    private final LocalCustomerRepository customerRepository;
    private static final String TOPIC = Topics.CUSTOMER_EVENTS;
    private static final String GROUP_ID = "invoice-service-group";

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID, containerFactory = "customerEventKafkaListenerFactory")
    public void consume(CustomerEventDTO event) {
        switch (event.getEventType()) {
            case CREATED, UPDATED -> customerRepository.save(LocalCustomer.builder()
                    .customerId(event.getCustomerId())
                    .name(event.getName())
                    .email(event.getEmail())
                    .build());
            case DELETED -> customerRepository.deleteById(event.getCustomerId());
        }
    }
}