package com.example.invoice.export_service.service;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.mapper.ExportMapper;
import com.example.invoice.export_service.repository.ExportCustomerRepository;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import com.example.invoice.common.inbox.ProcessedEvent;
import com.example.invoice.common.inbox.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceEventHandlerService {

    private final ExportCustomerRepository customerRepository;
    private final ExportInvoiceRepository invoiceRepository;
    private final ExportInvoiceItemRepository invoiceItemRepository;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * Projects an invoice event into export-service's read model.
     *
     * PDFs are generated on demand by ExportController, not here. This method
     * used to create a temp directory and write a PDF into it that nothing ever
     * read or deleted — one orphaned file per event.
     */
    @Transactional
    public void processInvoiceEvent(InvoiceEventDTO event, String eventId) {
        // Recorded in the same transaction as the projection, so "processed"
        // and "the effects of processing" can never disagree.
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        customerRepository.save(ExportMapper.toExportCustomer(event));
        ExportInvoice invoice = invoiceRepository.save(ExportMapper.toExportInvoice(event));
        invoiceItemRepository.deleteAll(invoiceItemRepository.findAllByInvoice(invoice));
        invoiceItemRepository.saveAll(ExportMapper.toExportInvoiceItems(event, invoice));

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(event.getEventType() != null ? event.getEventType().name() : null)
                    .processedAt(LocalDateTime.now())
                    .build());
        }
    }
}