package com.example.invoice.invoice_service.service;

import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.common.outbox.OutboxWriter;
import com.example.invoice.invoice_service.entity.PdfRequest;
import com.example.invoice.invoice_service.enums.PdfRequestStatus;
import com.example.invoice.invoice_service.exception.InvoiceNotFoundException;
import com.example.invoice.invoice_service.exception.PdfRequestNotFoundException;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.PdfRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfRequestService {

    private static final String AGGREGATE = "PdfRequest";

    private final PdfRequestRepository pdfRequestRepository;
    private final InvoiceRepository invoiceRepository;
    private final OutboxWriter outbox;

    @Value("${pdf.request-timeout-minutes:15}")
    private long timeoutMinutes;

    /**
     * The row and the event commit together. A direct Kafka send after the save
     * could leave a request that nothing will ever act on, stuck PENDING with
     * no way to tell it apart from one still in flight.
     */
    @Transactional
    public PdfRequest request(Long invoiceId) {
        if (!invoiceRepository.existsById(invoiceId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }

        PdfRequest request = pdfRequestRepository.save(PdfRequest.builder()
                .requestId(UUID.randomUUID())
                .invoiceId(invoiceId)
                .status(PdfRequestStatus.PENDING)
                .requestedAt(Instant.now())
                .build());

        outbox.record(AGGREGATE, request.getRequestId().toString(),
                Topics.INVOICE_PDF_REQUESTED, "PDF_REQUESTED",
                PdfRequestEventDTO.builder()
                        .requestId(request.getRequestId().toString())
                        .invoiceId(invoiceId)
                        .build());

        log.info("PDF requested for invoice {} as {}", invoiceId, request.getRequestId());
        return request;
    }

    @Transactional(readOnly = true)
    public PdfRequest status(UUID requestId) {
        return pdfRequestRepository.findById(requestId)
                .orElseThrow(() -> new PdfRequestNotFoundException(requestId));
    }

    /**
     * Marks requests that have been PENDING too long as FAILED.
     *
     * Called on a schedule and directly by tests. Returns the count so the
     * caller can log it; a sweep that silently does nothing is indistinguishable
     * from a sweep that is not running.
     */
    @Transactional
    public int failStaleRequests() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<PdfRequest> stale = pdfRequestRepository.findByStatusAndRequestedAtBefore(PdfRequestStatus.PENDING,
                cutoff);

        stale.forEach(request -> {
            request.setStatus(PdfRequestStatus.FAILED);
            request.setCompletedAt(Instant.now());
            log.warn("PDF request {} for invoice {} timed out after {}m",
                    request.getRequestId(), request.getInvoiceId(), timeoutMinutes);
        });
        pdfRequestRepository.saveAll(stale);

        return stale.size();
    }
}