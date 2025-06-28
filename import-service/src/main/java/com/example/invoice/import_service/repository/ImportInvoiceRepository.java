package com.example.invoice.import_service.repository;

import com.example.invoice.import_service.entity.ImportInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportInvoiceRepository extends JpaRepository<ImportInvoice, Long> {
}