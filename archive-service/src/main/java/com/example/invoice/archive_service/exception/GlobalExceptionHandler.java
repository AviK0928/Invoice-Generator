package com.example.invoice.archive_service.exception;

import com.example.invoice.common.web.BaseExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.invoice.archive_service")
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ArchivedInvoiceNotFoundException.class)
    public ProblemDetail handleNotFound(ArchivedInvoiceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Archived invoice not found",
                ex.getMessage(), "archived-invoice-not-found");
    }
}