package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

//@Profile("!no-db")
@Component
@RequiredArgsConstructor
public class UnarchiveConsumer {

    private final InvoiceRepository invoiceRepository;

    @KafkaListener(
            topics = "unarchive-invoices",
            groupId = "invoice-service-group",
            containerFactory = "archiveEventKafkaListenerContainerFactory"
    )
    public void consume(ArchiveEventDTO event) {
        if (event.getEventType() == ArchiveEventType.UNARCHIVE) {
            invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
                invoice.setArchived(false);
                invoiceRepository.save(invoice);
            });
        }
    }
}
