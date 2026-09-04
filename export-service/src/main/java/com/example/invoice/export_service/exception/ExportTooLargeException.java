package com.example.invoice.export_service.exception;

public class ExportTooLargeException extends RuntimeException {
    public ExportTooLargeException(Object month, int found, int max) {
        super("Export for " + month + " contains " + found
                + " invoices, which exceeds the limit of " + max + ".");
    }
}