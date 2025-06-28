package com.example.invoice.archive_service.repository;

import com.example.invoice.archive_service.entity.ArchivedInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchivedInvoiceItemRepository extends JpaRepository<ArchivedInvoiceItem, Long> {
}
