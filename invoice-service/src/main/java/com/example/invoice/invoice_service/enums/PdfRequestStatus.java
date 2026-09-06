package com.example.invoice.invoice_service.enums;

/**
 * PENDING until export-service reports back, then READY.
 *
 * FAILED is set by a sweep, not by an event. export-service cannot always tell
 * invoice-service it failed — a render that throws dead-letters, and a request
 * lost before it ever arrived produces nothing at all. A timeout covers both,
 * and "we stopped waiting" is an honest thing to tell a user.
 *
 * DOWNLOADED is unreachable: the download happens in export-service, which does
 * not report it back. Kept because the status is part of the API contract and
 * removing it is a breaking change for no gain; a client should treat it as
 * READY.
 */
public enum PdfRequestStatus {
    PENDING,
    READY,
    DOWNLOADED,
    FAILED
}