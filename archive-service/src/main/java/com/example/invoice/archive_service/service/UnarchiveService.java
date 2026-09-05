package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.repository.ArchivedInvoiceItemRepository;
import com.example.invoice.archive_service.repository.ArchivedInvoiceRepository;
import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnarchiveService {

        private static final String AGGREGATE = "Invoice";

        private final ArchivedInvoiceRepository invoiceRepository;
        private final ArchivedInvoiceItemRepository itemRepository;
        private final OutboxWriter outbox;

        /**
         * Removing the archive and telling invoice-service to restore the invoice
         * commit together. Previously the publish could fail after the delete,
         * leaving the invoice archived nowhere and active nowhere.
         */
        @Transactional
        public void unarchiveInvoice(Long invoiceId) {
                itemRepository.deleteByInvoiceId(invoiceId);
                invoiceRepository.findByInvoiceId(invoiceId).ifPresent(invoiceRepository::delete);

                outbox.record(AGGREGATE, invoiceId, Topics.UNARCHIVE_INVOICES,
                                ArchiveEventType.UNARCHIVE.name(),
                                ArchiveEventDTO.builder()
                                                .invoiceId(invoiceId)
                                                .eventType(ArchiveEventType.UNARCHIVE)
                                                .build());
        }
}