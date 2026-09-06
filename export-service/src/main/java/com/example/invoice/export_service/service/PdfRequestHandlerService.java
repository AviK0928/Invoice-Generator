package com.example.invoice.export_service.service;

import com.example.invoice.common.inbox.ProcessedEvent;
import com.example.invoice.common.inbox.ProcessedEventRepository;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.common.outbox.OutboxWriter;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.PdfDocument;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import com.example.invoice.export_service.repository.PdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfRequestHandlerService {

    private static final String AGGREGATE = "PdfDocument";

    private final ExportInvoiceRepository invoiceRepository;
    private final PdfDocumentRepository pdfDocumentRepository;
    private final PdfExportService pdfExportService;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxWriter outbox;

    /**
     * Renders the PDF and announces it, in one transaction.
     *
     * The invoice comes from this service's own read model rather than the
     * event payload — the projection is already maintained from invoice-events,
     * so sending a snapshot would duplicate data that could disagree with it.
     *
     * A request for an invoice this service has not projected yet throws, which
     * dead-letters the record. That is the honest outcome: the event arrived
     * before the invoice, and retrying later is exactly what should happen.
     */
    @Transactional
    public void handle(PdfRequestEventDTO event, String eventId) {
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        UUID requestId = UUID.fromString(event.getRequestId());
        ExportInvoice invoice = invoiceRepository.findById(event.getInvoiceId())
                .orElseThrow(() -> new IllegalStateException(
                        "No projected invoice " + event.getInvoiceId()
                                + " for PDF request " + requestId));

        byte[] content;
        try {
            content = pdfExportService.generatePdfForInvoice(invoice);
        } catch (Exception e) {
            // Wrapped rather than swallowed: a failure here must roll the
            // transaction back and dead-letter, not leave the request PENDING
            // forever with no record of why.
            throw new IllegalStateException("Failed to render PDF for request " + requestId, e);
        }

        pdfDocumentRepository.save(PdfDocument.builder()
                .requestId(requestId)
                .invoiceId(event.getInvoiceId())
                .content(content)
                .createdAt(LocalDateTime.now())
                .build());

        outbox.record(AGGREGATE, requestId.toString(),
                Topics.INVOICE_PDF_READY, "PDF_READY", event);

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType("PDF_REQUESTED")
                    .processedAt(LocalDateTime.now())
                    .build());
        }

        log.info("Generated PDF for invoice {} as request {} ({} bytes)",
                event.getInvoiceId(), requestId, content.length);
    }
}