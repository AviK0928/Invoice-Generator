package com.example.invoice.invoice_service.dto;

import com.example.invoice.invoice_service.enums.PdfRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfRequestResponseDTO {
    private UUID requestId;
    private Long invoiceId;
    private PdfRequestStatus status;
    private Instant requestedAt;
    private Instant completedAt;

    /** Where the bytes will be, once status is READY. Null until then. */
    private String downloadUrl;
}