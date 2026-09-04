package com.example.invoice.export_service.exception;

public class ExportInvoiceNotFoundException extends RuntimeException {
    public ExportInvoiceNotFoundException(Long invoiceId) {
        super("No export record found for invoiceId: " + invoiceId);
    }
}