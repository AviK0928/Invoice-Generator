package com.example.invoice.invoice_service.enums;

/**
 * PENDING until export-service reports back. There is no FAILED state yet:
 * a generation failure dead-letters in export-service and the request stays
 * PENDING, which is visible but not explained. Noted as a gap.
 */
public enum PdfRequestStatus {
    PENDING,
    READY,
    DOWNLOADED
}