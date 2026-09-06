package com.example.invoice.common.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Both directions of the PDF flow. Deliberately thin: export-service already
 * holds the invoice in its own read model, populated from invoice-events, so
 * sending a snapshot would duplicate data that is already there and could
 * disagree with it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfRequestEventDTO {
    private String requestId;
    private Long invoiceId;
}