package com.example.invoice.customer_service.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("A customer with email " + email + " already exists.");
    }
}