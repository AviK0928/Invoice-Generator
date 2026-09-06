package com.example.invoice.export_service.repository;

import com.example.invoice.export_service.entity.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PdfDocumentRepository extends JpaRepository<PdfDocument, UUID> {
}