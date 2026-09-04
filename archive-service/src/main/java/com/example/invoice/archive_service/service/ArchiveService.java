package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.dto.ArchiveResponseDTO;
import com.example.invoice.archive_service.entity.ArchivedInvoice;
import com.example.invoice.archive_service.entity.ArchivedInvoiceItem;
import com.example.invoice.archive_service.exception.ArchivedInvoiceNotFoundException;
import com.example.invoice.archive_service.mapper.ArchiveMapper;
import com.example.invoice.archive_service.repository.ArchivedInvoiceItemRepository;
import com.example.invoice.archive_service.repository.ArchivedInvoiceRepository;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchivedInvoiceRepository archivedInvoiceRepository;
    private final ArchivedInvoiceItemRepository archivedInvoiceItemRepository;
    private final ArchiveMapper archiveMapper;

    @Transactional
    public void saveArchivedInvoice(ArchiveEventDTO dto) {
        archivedInvoiceRepository.save(archiveMapper.toEntity(dto));
        archivedInvoiceItemRepository.saveAll(
                archiveMapper.toItemEntities(dto.getItems(), dto.getInvoiceId()));
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