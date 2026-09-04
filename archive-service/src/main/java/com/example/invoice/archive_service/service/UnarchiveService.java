package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.kafka.UnarchiveProducer;
import com.example.invoice.archive_service.repository.ArchivedInvoiceItemRepository;
import com.example.invoice.archive_service.repository.ArchivedInvoiceRepository;
import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import org.springframework.transaction.annotation.Transactional;
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
                itemRepository.deleteByInvoiceId(invoiceId);
                invoiceRepository.findByInvoiceId(invoiceId).ifPresent(invoiceRepository::delete);
                producer.publish(ArchiveEventDTO.builder()
                                .invoiceId(invoiceId)
                                .eventType(ArchiveEventType.UNARCHIVE)
                                .build());
        }
}
