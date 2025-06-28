package com.example.invoice.customer_service.service;

import com.example.invoice.common.enums.EventType;
import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import com.example.invoice.customer_service.dto.CustomerRequestDTO;
import com.example.invoice.customer_service.dto.CustomerResponseDTO;
import com.example.invoice.customer_service.entity.Customer;
import com.example.invoice.customer_service.kafka.CustomerEventProducer;
import com.example.invoice.customer_service.mapper.CustomerMapper;
import com.example.invoice.customer_service.repository.CustomerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerEventProducer eventProducer;

    @Transactional
    public CustomerResponseDTO createCustomer(CustomerRequestDTO dto) {
        Customer customer = CustomerMapper.toEntity(dto);
        customer = customerRepository.save(customer);

        CustomerEventDTO event = CustomerEventDTO.builder()
                .customerId(customer.getCustomerId())
                .name(customer.getName())
                .email(customer.getEmail())
                .eventType(EventType.CREATED)
                .build();

        eventProducer.publish(event);
        return CustomerMapper.toDTO(customer);
    }

    @Transactional
    public void deleteCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        customerRepository.delete(customer);

        CustomerEventDTO event = CustomerEventDTO.builder()
                .customerId(customer.getCustomerId())
                .name(customer.getName())
                .email(customer.getEmail())
                .eventType(EventType.DELETED)
                .build();

        eventProducer.publish(event); 
    }
}