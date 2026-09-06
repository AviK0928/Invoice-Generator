package com.example.invoice.customer_service.exception;

import com.example.invoice.common.web.BaseExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.invoice.customer_service")
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleNotFound(CustomerNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Customer not found",
                ex.getMessage(), "customer-not-found");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicate(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate email",
                ex.getMessage(), "duplicate-email");
    }
}