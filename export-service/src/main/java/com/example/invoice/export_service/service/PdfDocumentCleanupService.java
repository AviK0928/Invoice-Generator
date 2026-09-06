package com.example.invoice.export_service.service;

import com.example.invoice.export_service.repository.PdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Deletes PDFs nobody downloaded.
 *
 * A downloaded PDF removes itself, so this only ever sees abandoned ones — a
 * user who requested and walked away. Without it they accumulate forever, and
 * these are the largest rows in the database. idx_pdf_documents_created_at
 * exists for this query.
 *
 * Retention is hours, not days: the request that produced it has already timed
 * out in invoice-service after fifteen minutes, so the client has long since
 * been told to ask again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfDocumentCleanupService {

    private final PdfDocumentRepository pdfDocumentRepository;

    @Value("${pdf.retention-hours:24}")
    private long retentionHours;

    @Transactional
    public long deleteExpired() {
        long deleted = pdfDocumentRepository.deleteByCreatedAtBefore(
                LocalDateTime.now().minusHours(retentionHours));

        if (deleted > 0) {
            log.info("Deleted {} undownloaded PDF(s) older than {}h", deleted, retentionHours);
        }
        return deleted;
    }
}