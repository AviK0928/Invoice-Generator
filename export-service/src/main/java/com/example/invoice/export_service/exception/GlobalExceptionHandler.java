package com.example.invoice.export_service.exception;

import com.example.invoice.common.web.BaseExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ExportInvoiceNotFoundException.class)
    public ProblemDetail handleNotFound(ExportInvoiceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Invoice not found",
                ex.getMessage(), "export-invoice-not-found");
    }

    /** The in-memory assembly cap. Without this the guard would return 500. */
    @ExceptionHandler(ExportTooLargeException.class)
    public ProblemDetail handleTooLarge(ExportTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large",
                ex.getMessage(), "export-too-large");
    }
}