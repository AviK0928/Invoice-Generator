package com.example.invoice.invoice_service.repository;

import com.example.invoice.invoice_service.entity.PdfRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PdfRequestRepository extends JpaRepository<PdfRequest, UUID> {
}