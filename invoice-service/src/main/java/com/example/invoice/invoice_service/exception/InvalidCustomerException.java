package com.example.invoice.invoice_service.exception;

public class InvalidCustomerException extends RuntimeException {
    public InvalidCustomerException(Long customerId) {
        super("Unknown customer: " + customerId);
    }
}