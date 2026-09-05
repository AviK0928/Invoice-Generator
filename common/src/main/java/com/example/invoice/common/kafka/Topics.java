package com.example.invoice.common.kafka;

/**
 * Every topic in the system, in one place.
 *
 * Topic names were previously private constants in each producer and consumer,
 * so a mismatch was invisible: with auto-create enabled, a typo silently makes
 * a new topic and the producer or consumer then talks to nothing.
 */
public final class Topics {

    private Topics() {
    }

    /** customer-service -> invoice-service, export-service */
    public static final String CUSTOMER_EVENTS = "customer-events";

    /** invoice-service -> export-service */
    public static final String INVOICE_EVENTS = "invoice-events";

    /** invoice-service -> archive-service */
    public static final String INVOICE_ARCHIVED = "invoice-archived";

    /** archive-service -> invoice-service */
    public static final String INVOICE_DELETE = "invoice-delete";

    /** archive-service -> invoice-service */
    public static final String UNARCHIVE_INVOICES = "unarchive-invoices";

    /**
     * import-service -> nothing.
     *
     * The only consumer was InvoiceImportedConsumer, which was entirely
     * commented out and deleted in Phase 0. Imported invoices are persisted to
     * importdb and published here, where no service is listening. See the
     * engineering log.
     */
    public static final String INVOICE_IMPORTED = "invoice-imported";

    /** Suffix appended by DeadLetterPublishingRecoverer. */
    public static final String DLT_SUFFIX = "-dlt";
}