package com.example.invoice.archive_service.exception;

public class ArchivedInvoiceNotFoundException extends RuntimeException {
    public ArchivedInvoiceNotFoundException(Long invoiceId) {
        super("No archived invoice found for invoiceId: " + invoiceId);
    }
}