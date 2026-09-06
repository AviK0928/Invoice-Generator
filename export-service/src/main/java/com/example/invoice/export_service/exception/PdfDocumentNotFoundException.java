package com.example.invoice.export_service.exception;

import java.util.UUID;

public class PdfDocumentNotFoundException extends RuntimeException {
    public PdfDocumentNotFoundException(UUID requestId) {
        super("No PDF for request: " + requestId
                + ". It may not be ready yet, or it has already been downloaded.");
    }
}