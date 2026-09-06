package com.example.invoice.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework-level exception handling shared by every service.
 *
 * Deliberately NOT annotated with @RestControllerAdvice — that belongs on the
 * concrete subclass. Annotating both would register two advices for the same
 * handlers.
 *
 * Subclasses add only their own domain exceptions and call {@link #problem}.
 * Subclasses MUST scope themselves with basePackages. Five unscoped advices in
 * one context is not five contracts, it is one: the resolver takes the first
 * advice with a matching method, and the inherited handler on Exception matches
 * everything, so the first advice registered answers for the whole application.
 * Four modules returned "internal server error" for their own domain 404s in
 * production before this was scoped. The @WebMvcTest slices cannot catch it —
 * each registers exactly one advice.
 */
public abstract class BaseExceptionHandler {

    protected static final String ERROR_TYPE_BASE = "https://invoice-generator/errors/";

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));

        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid.", "validation-failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body",
                "The request body could not be parsed. Check field types and enum values.",
                "malformed-body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '" + ex.getName() + "' has an invalid value.", "invalid-parameter");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing parameter",
                "Required parameter '" + ex.getParameterName() + "' is not present.",
                "missing-parameter");
    }

    /**
     * A sort parameter naming something that is not sortable.
     *
     * Two exceptions reach here depending on the repository. A derived query
     * throws PropertyReferenceException from Spring Data's mapping layer; a
     * declared @Query throws InvalidDataAccessApiUsageException from
     * JpaQueryTransformerSupport, which validates the sort against the select
     * clause. Neither was mapped, so both landed in the catch-all and a bad
     * query parameter was reported as a server error.
     *
     * Swagger UI makes this the default experience rather than an edge case:
     * it prefills Pageable's sort parameter with ["string"], so the first
     * Execute on any paged endpoint returned a 500.
     *
     * The exception message is not echoed back — it names internal query
     * structure and suggests JpaSort.unsafe, neither of which is a client's
     * business. Logged at warn instead, because InvalidDataAccessApiUsageException
     * can also signal genuine server-side misuse of the Data API, and mapping
     * it to 400 would otherwise hide that entirely.
     */
    @ExceptionHandler({ PropertyReferenceException.class,
            InvalidDataAccessApiUsageException.class })
    public ProblemDetail handleInvalidSort(Exception ex) {
        log.warn("Rejected data-access request as a bad parameter", ex);
        return problem(HttpStatus.BAD_REQUEST, "Invalid sort parameter",
                "The sort parameter names a property that cannot be sorted on.",
                "invalid-sort");
    }

    /** Safety net for unique constraints when a pre-check loses a race. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The request conflicts with existing data.", "conflict");
    }

    /**
     * Full detail logged server-side; the client learns nothing about internals.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.", "internal-error");
    }

    protected ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(ERROR_TYPE_BASE + type));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Spring throws this for an unmapped path. It extends ServletException, so
     * without an explicit handler it lands in the catch-all and a typo in a URL
     * is reported as a server error.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found",
                "No endpoint matches that path.", "no-such-endpoint");
    }
}