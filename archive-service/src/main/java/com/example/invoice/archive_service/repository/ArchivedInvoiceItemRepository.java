package com.example.invoice.archive_service.repository;

import com.example.invoice.archive_service.entity.ArchivedInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchivedInvoiceItemRepository extends JpaRepository<ArchivedInvoiceItem, Long> {

    List<ArchivedInvoiceItem> findByInvoiceId(Long invoiceId);

    void deleteByInvoiceId(Long invoiceId);
}