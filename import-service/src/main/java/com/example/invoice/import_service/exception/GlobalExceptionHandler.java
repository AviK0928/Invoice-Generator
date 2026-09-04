package com.example.invoice.import_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String BASE = "https://invoice-generator/errors/";

    @ExceptionHandler(ImportValidationException.class)
    public ProblemDetail handleValidation(ImportValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid import file",
                ex.getMessage(), "import-invalid");
    }

    // Must stay above handleMissingFile: MaxUploadSizeExceededException extends
    // MultipartException, and the more specific handler has to be found first.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large",
                "The uploaded file exceeds the maximum allowed size.", "file-too-large");
    }

    /**
     * A request with no file can surface as any of these depending on whether
     * the body is absent, malformed, or not multipart at all.
     */
    @ExceptionHandler({ MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MultipartException.class,
            HttpMediaTypeNotSupportedException.class })
    public ProblemDetail handleMissingFile(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing file",
                "A multipart form field named 'file' is required.", "missing-file");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(BASE + type));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}