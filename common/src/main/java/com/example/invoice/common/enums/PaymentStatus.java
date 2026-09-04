package com.example.invoice.common.enums;

public enum PaymentStatus {
    /** Issued, not yet paid. The normal state of a new invoice. */
    PENDING,
    /** Past its due date and still unpaid. */
    OVERDUE,
    SUCCESSFUL,
    FAILED
}