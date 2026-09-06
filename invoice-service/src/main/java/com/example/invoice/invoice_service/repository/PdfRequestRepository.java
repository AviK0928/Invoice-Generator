package com.example.invoice.invoice_service.repository;

import com.example.invoice.invoice_service.entity.PdfRequest;
import com.example.invoice.invoice_service.enums.PdfRequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PdfRequestRepository extends JpaRepository<PdfRequest, UUID> {
    /**
     * Stale requests, oldest first. A modifying bulk update would be fewer
     * queries, but there are never many of these and loading them lets the
     * sweep log which ones it gave up on.
     */
    List<PdfRequest> findByStatusAndRequestedAtBefore(PdfRequestStatus status, Instant cutoff);
}