package com.example.invoice.invoice_service.exception;

import com.example.invoice.common.web.BaseExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ProblemDetail handleNotFound(InvoiceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Invoice not found",
                ex.getMessage(), "invoice-not-found");
    }

    @ExceptionHandler(InvalidCustomerException.class)
    public ProblemDetail handleInvalidCustomer(InvalidCustomerException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown customer",
                ex.getMessage(), "unknown-customer");
    }
}