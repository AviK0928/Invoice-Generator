package com.example.invoice.export_service.repository;

import com.example.invoice.export_service.entity.ExportInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExportInvoiceRepository extends JpaRepository<ExportInvoice, Long> {

    List<ExportInvoice> findByInvoiceDateBetween(LocalDate startDate, LocalDate endDate);
}