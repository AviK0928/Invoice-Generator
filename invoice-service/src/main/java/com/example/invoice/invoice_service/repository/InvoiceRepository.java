package com.example.invoice.invoice_service.repository;

import com.example.invoice.invoice_service.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByArchivedFalseAndInvoiceDateBefore(LocalDate cutoffDate);

}
