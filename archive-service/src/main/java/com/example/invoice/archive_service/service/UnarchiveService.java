package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.kafka.UnarchiveProducer;
import com.example.invoice.archive_service.repository.ArchivedInvoiceItemRepository;
import com.example.invoice.archive_service.repository.ArchivedInvoiceRepository;
import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnarchiveService {

    private final ArchivedInvoiceRepository invoiceRepository;
    private final ArchivedInvoiceItemRepository itemRepository;
    private final UnarchiveProducer producer;

    @Transactional
    public void unarchiveInvoice(Long invoiceId) {
        // Step 1: Delete all archived items for this invoice
        itemRepository.deleteAll(
                itemRepository.findAll().stream()
                        .filter(item -> item.getInvoiceId().equals(invoiceId))
                        .toList()
        );

        // Step 2: Delete archived invoice record
        invoiceRepository.findByInvoiceId(invoiceId)
                .ifPresent(invoiceRepository::delete);

        // Step 3: Emit UNARCHIVE event
        ArchiveEventDTO event = ArchiveEventDTO.builder()
                .invoiceId(invoiceId)
                .eventType(ArchiveEventType.UNARCHIVE)
                .build();

        producer.publish(event); // ✅ changed to standardized method name
    }
}
