package com.example.invoice.invoice_service.exception;

import java.util.UUID;

public class PdfRequestNotFoundException extends RuntimeException {
    public PdfRequestNotFoundException(UUID requestId) {
        super("PDF request not found: " + requestId);
    }
}