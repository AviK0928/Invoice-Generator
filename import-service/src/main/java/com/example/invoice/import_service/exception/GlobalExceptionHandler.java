package com.example.invoice.import_service.exception;

import com.example.invoice.common.web.BaseExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackages = "com.example.invoice.import_service")
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ImportValidationException.class)
    public ProblemDetail handleValidation(ImportValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid import file",
                ex.getMessage(), "import-invalid");
    }

    // Declared before the MultipartException handler below, which it extends.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large",
                "The uploaded file exceeds the maximum allowed size.", "file-too-large");
    }

    /** A request with no file surfaces as any of these, depending on the body. */
    @ExceptionHandler({ MissingServletRequestPartException.class,
            MultipartException.class,
            HttpMediaTypeNotSupportedException.class })
    public ProblemDetail handleMissingFile(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing file",
                "A multipart form field named 'file' is required.", "missing-file");
    }
}