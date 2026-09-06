package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.dto.ArchiveResponseDTO;
import com.example.invoice.archive_service.entity.ArchivedInvoice;
import com.example.invoice.archive_service.entity.ArchivedInvoiceItem;
import com.example.invoice.archive_service.exception.ArchivedInvoiceNotFoundException;
import com.example.invoice.archive_service.mapper.ArchiveMapper;
import com.example.invoice.archive_service.repository.ArchivedInvoiceItemRepository;
import com.example.invoice.archive_service.repository.ArchivedInvoiceRepository;
import com.example.invoice.common.inbox.ProcessedEvent;
import com.example.invoice.common.inbox.ProcessedEventRepository;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchivedInvoiceRepository archivedInvoiceRepository;
    private final ArchivedInvoiceItemRepository archivedInvoiceItemRepository;
    private final ArchiveMapper archiveMapper;

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Recorded in the same transaction as the archive, so "processed" and the
     * effects of processing can never disagree.
     *
     * Without this a redelivery hits uk_archived_invoices_invoice_id, throws,
     * and dead-letters — the archive stays correct, but at-least-once delivery
     * produces an alert about something working as intended.
     */
    @Transactional
    public void saveArchivedInvoice(ArchiveEventDTO dto, String eventId) {
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        archivedInvoiceRepository.save(archiveMapper.toEntity(dto));
        archivedInvoiceItemRepository.saveAll(
                archiveMapper.toItemEntities(dto.getItems(), dto.getInvoiceId()));

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(dto.getEventType() != null ? dto.getEventType().name() : null)
                    .processedAt(LocalDateTime.now())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public boolean existsByInvoiceId(Long invoiceId) {
        return archivedInvoiceRepository.existsByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public ArchiveResponseDTO getArchivedInvoice(Long invoiceId) {
        ArchivedInvoice invoice = archivedInvoiceRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ArchivedInvoiceNotFoundException(invoiceId));
        return toDTO(invoice, archivedInvoiceItemRepository.findByInvoiceId(invoiceId));
    }

    @Transactional(readOnly = true)
    public Page<ArchiveResponseDTO> listArchived(Long customerId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        return archivedInvoiceRepository.search(customerId, fromDate, toDate, pageable)
                .map(inv -> toDTO(inv,
                        archivedInvoiceItemRepository.findByInvoiceId(inv.getInvoiceId())));
    }

    private ArchiveResponseDTO toDTO(ArchivedInvoice invoice, List<ArchivedInvoiceItem> items) {
        return ArchiveResponseDTO.builder()
                .invoiceId(invoice.getInvoiceId())
                .customerId(invoice.getCustomerId())
                .name(invoice.getName())
                .email(invoice.getEmail())
                .invoiceDate(invoice.getInvoiceDate())
                .paymentStatus(invoice.getPaymentStatus())
                .totalAmount(invoice.getTotalAmount())
                .eventType(invoice.getEventType())
                .items(archiveMapper.toItemDTOs(items))
                .build();
    }
}