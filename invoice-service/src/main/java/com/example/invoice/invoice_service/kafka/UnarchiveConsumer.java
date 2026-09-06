package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * No inbox check, deliberately. Clearing the archived flag on an invoice that
 * is already unarchived changes nothing, and an invoice that no longer exists
 * is skipped by ifPresent — so a redelivery produces the same state. An inbox
 * would add a table write per event to prevent nothing. Consumers whose
 * effects are not idempotent (archive-service's archive, export-service's PDF)
 * do check.
 */
@Component
@RequiredArgsConstructor
public class UnarchiveConsumer {

    private final InvoiceRepository invoiceRepository;

    @KafkaListener(topics = Topics.UNARCHIVE_INVOICES, groupId = "invoice-service-group", containerFactory = "archiveResponseKafkaListenerFactory")
    public void consume(ArchiveEventDTO event) {
        if (event.getEventType() == ArchiveEventType.UNARCHIVE) {
            invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
                invoice.setArchived(false);
                invoiceRepository.save(invoice);
            });
        }
    }
}
