package com.example.invoice.invoice_service.entity;

import com.example.invoice.invoice_service.enums.PdfRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pdf_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfRequest {

    /**
     * Assigned by the application, not the database. The client is handed this
     * id in the 202 response before anything asynchronous has happened, so it
     * has to exist before the row is written.
     */
    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PdfRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}