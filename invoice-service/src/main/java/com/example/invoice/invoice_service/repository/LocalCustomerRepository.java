package com.example.invoice.invoice_service.repository;

import com.example.invoice.invoice_service.entity.LocalCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCustomerRepository extends JpaRepository<LocalCustomer, Long> {
}