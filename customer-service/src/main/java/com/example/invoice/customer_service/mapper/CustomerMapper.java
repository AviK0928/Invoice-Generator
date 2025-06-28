package com.example.invoice.customer_service.mapper;

import com.example.invoice.customer_service.dto.CustomerRequestDTO;
import com.example.invoice.customer_service.dto.CustomerResponseDTO;
import com.example.invoice.customer_service.entity.Customer;

public class CustomerMapper {

    public static Customer toEntity(CustomerRequestDTO dto) {
        return Customer.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .build();
    }

    public static CustomerResponseDTO toDTO(Customer entity) {
        return CustomerResponseDTO.builder()
                .name(entity.getName())
                .email(entity.getEmail())
                .build();
    }
}