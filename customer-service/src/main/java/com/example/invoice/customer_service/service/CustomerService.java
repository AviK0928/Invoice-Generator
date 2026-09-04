package com.example.invoice.customer_service.service;

import com.example.invoice.common.enums.EventType;
import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import com.example.invoice.customer_service.dto.CustomerRequestDTO;
import com.example.invoice.customer_service.dto.CustomerResponseDTO;
import com.example.invoice.customer_service.entity.Customer;
import com.example.invoice.customer_service.exception.CustomerNotFoundException;
import com.example.invoice.customer_service.exception.DuplicateEmailException;
import com.example.invoice.customer_service.kafka.CustomerEventProducer;
import com.example.invoice.customer_service.mapper.CustomerMapper;
import com.example.invoice.customer_service.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerEventProducer eventProducer;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public CustomerResponseDTO getCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .map(CustomerMapper::toDTO)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponseDTO> listCustomers(String search, Pageable pageable) {
        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.toLowerCase() + "%";
        return customerRepository.search(pattern, pageable).map(CustomerMapper::toDTO);
    }

    // --------------------------------------------------------------- commands

    @Transactional
    public CustomerResponseDTO createCustomer(CustomerRequestDTO dto) {
        if (customerRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateEmailException(dto.getEmail());
        }

        Customer customer = customerRepository.save(CustomerMapper.toEntity(dto));
        eventProducer.publish(toEvent(customer, EventType.CREATED));
        return CustomerMapper.toDTO(customer);
    }

    @Transactional
    public CustomerResponseDTO updateCustomer(Long customerId, CustomerRequestDTO dto) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        if (customerRepository.existsByEmailAndCustomerIdNot(dto.getEmail(), customerId)) {
            throw new DuplicateEmailException(dto.getEmail());
        }

        customer.setName(dto.getName());
        customer.setEmail(dto.getEmail());
        customer = customerRepository.save(customer);

        eventProducer.publish(toEvent(customer, EventType.UPDATED));
        return CustomerMapper.toDTO(customer);
    }

    @Transactional
    public void deleteCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        customerRepository.delete(customer);
        eventProducer.publish(toEvent(customer, EventType.DELETED));
    }

    // -------------------------------------------------------------- internals

    private CustomerEventDTO toEvent(Customer customer, EventType type) {
        return CustomerEventDTO.builder()
                .customerId(customer.getCustomerId())
                .name(customer.getName())
                .email(customer.getEmail())
                .eventType(type)
                .build();
    }
}