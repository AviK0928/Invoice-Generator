package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.enums.EventType;
import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

//@Profile("!no-db")
@Component
@RequiredArgsConstructor
public class CustomerEventConsumer {

    private final LocalCustomerRepository customerRepository;
    private static final String TOPIC = "customer-events";
    private static final String GROUP_ID = "invoice-service-group";

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID, containerFactory = "kafkaListenerContainerFactory")
    public void consume(CustomerEventDTO event) {
        if (event.getEventType() == EventType.CREATED) {
            LocalCustomer customer = LocalCustomer.builder()
                    .customerId(event.getCustomerId())
                    .name(event.getName())
                    .email(event.getEmail())
                    .build();
            customerRepository.save(customer);
        } else if (event.getEventType() == EventType.DELETED) {
            customerRepository.deleteById(event.getCustomerId());
        }
    }
}