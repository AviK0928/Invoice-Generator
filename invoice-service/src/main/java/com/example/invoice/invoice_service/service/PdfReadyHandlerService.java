package com.example.invoice.invoice_service.service;

import com.example.invoice.common.inbox.ProcessedEvent;
import com.example.invoice.common.inbox.ProcessedEventRepository;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.invoice_service.entity.PdfRequest;
import com.example.invoice.invoice_service.enums.PdfRequestStatus;
import com.example.invoice.invoice_service.repository.PdfRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReadyHandlerService {

    private final PdfRequestRepository pdfRequestRepository;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * Marks a request READY once export-service has the bytes.
     *
     * An unknown requestId is logged and dropped rather than thrown. The event
     * is not wrong — the request row may have been deleted with its invoice —
     * and dead-lettering it would raise an alert about something that is
     * working as intended.
     */
    @Transactional
    public void handle(PdfRequestEventDTO event, String eventId) {
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        UUID requestId = UUID.fromString(event.getRequestId());
        PdfRequest request = pdfRequestRepository.findById(requestId).orElse(null);

        if (request == null) {
            log.warn("PDF ready for unknown request {}; ignoring", requestId);
        } else {
            request.setStatus(PdfRequestStatus.READY);
            request.setCompletedAt(Instant.now());
            pdfRequestRepository.save(request);
            log.info("PDF ready for request {} (invoice {})", requestId, event.getInvoiceId());
        }

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType("PDF_READY")
                    .processedAt(LocalDateTime.now())
                    .build());
        }
    }
}