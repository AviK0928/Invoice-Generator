-- A user's request for a PDF. invoice-service owns the status; export-service
-- owns the bytes. Deliberately separate: the request outlives the artifact,
-- which is deleted on first download.
CREATE TABLE pdf_requests (
    request_id   UUID PRIMARY KEY,
    invoice_id   BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_pdf_requests_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (invoice_id) ON DELETE CASCADE
);

CREATE INDEX idx_pdf_requests_invoice_id ON pdf_requests (invoice_id);