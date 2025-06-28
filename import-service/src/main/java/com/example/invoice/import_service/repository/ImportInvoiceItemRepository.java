package com.example.invoice.import_service.repository;

import com.example.invoice.import_service.entity.ImportInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportInvoiceItemRepository extends JpaRepository<ImportInvoiceItem, Long> {
}
