package com.example.invoice.invoice_service.repository;

import com.example.invoice.invoice_service.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    void deleteByInvoice_InvoiceId(Long invoiceId);
    void deleteAllByInvoice_InvoiceId(Long invoiceId);
}