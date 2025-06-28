package com.example.invoice.export_service.repository;

import com.example.invoice.export_service.entity.ExportCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportCustomerRepository extends JpaRepository<ExportCustomer, Long> {
}