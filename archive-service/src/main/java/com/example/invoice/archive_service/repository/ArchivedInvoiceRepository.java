package com.example.invoice.archive_service.repository;

import com.example.invoice.archive_service.entity.ArchivedInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ArchivedInvoiceRepository extends JpaRepository<ArchivedInvoice, Long> {

    List<ArchivedInvoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);

    List<ArchivedInvoice> findByInvoiceDateBefore(LocalDate cutoffDate);

    Optional<ArchivedInvoice> findByInvoiceId(Long invoiceId);
}
