package com.example.invoice.import_service.exception;

public class ImportValidationException extends RuntimeException {
    public ImportValidationException(String message) {
        super(message);
    }
}