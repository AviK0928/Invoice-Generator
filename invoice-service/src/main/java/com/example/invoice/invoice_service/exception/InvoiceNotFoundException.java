package com.example.invoice.invoice_service.exception;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(Long invoiceId) {
        super("Invoice not found: " + invoiceId);
    }
}