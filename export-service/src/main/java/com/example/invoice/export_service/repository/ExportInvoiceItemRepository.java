package com.example.invoice.export_service.repository;

import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportInvoiceItemRepository extends JpaRepository<ExportInvoiceItem, Long> {
    List<ExportInvoiceItem> findAllByInvoice(ExportInvoice invoice);
}