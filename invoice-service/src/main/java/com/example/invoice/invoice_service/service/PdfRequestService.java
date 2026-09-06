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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfRequestService {

    private static final String AGGREGATE = "PdfRequest";

    private final PdfRequestRepository pdfRequestRepository;
    private final InvoiceRepository invoiceRepository;
    private final OutboxWriter outbox;

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
}