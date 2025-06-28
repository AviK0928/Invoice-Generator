package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.dto.ArchiveResponseDTO;
import com.example.invoice.archive_service.entity.ArchivedInvoice;
import com.example.invoice.archive_service.entity.ArchivedInvoiceItem;
import com.example.invoice.archive_service.kafka.InvoiceDeletionProducer;
import com.example.invoice.archive_service.mapper.ArchiveMapper;
import com.example.invoice.archive_service.repository.ArchivedInvoiceItemRepository;
import com.example.invoice.archive_service.repository.ArchivedInvoiceRepository;
import com.example.invoice.common.enums.InvoiceEventType;
import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchivedInvoiceRepository archivedInvoiceRepository;
    private final ArchivedInvoiceItemRepository archivedInvoiceItemRepository;
    private final ArchiveMapper archiveMapper;
    private final ExportService exportService;
    private final InvoiceDeletionProducer invoiceDeletionProducer;

    public void saveArchivedInvoice(ArchiveEventDTO dto) {
        ArchivedInvoice invoice = archiveMapper.toEntity(dto);
        archivedInvoiceRepository.save(invoice);

        List<ArchivedInvoiceItem> items = archiveMapper.toItemEntities(dto.getItems(), dto.getInvoiceId());
        archivedInvoiceItemRepository.saveAll(items);
    }

    public boolean existsByInvoiceId(Long invoiceId) {
        return archivedInvoiceRepository.findByInvoiceId(invoiceId).isPresent();
    }

    public Optional<ArchiveResponseDTO> getArchivedInvoiceDetails(Long invoiceId) {
        return archivedInvoiceRepository.findByInvoiceId(invoiceId)
                .map(invoice -> {
                    List<ArchivedInvoiceItem> items = archivedInvoiceItemRepository.findAll().stream()
                            .filter(item -> item.getInvoiceId().equals(invoiceId))
                            .toList();

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
                });
    }

    public void exportAndDeleteExpiredArchives() {
        LocalDate cutoffDate = LocalDate.now().minusMonths(1);
        List<ArchivedInvoice> expiredInvoices = archivedInvoiceRepository.findByInvoiceDateBefore(cutoffDate);

        if (expiredInvoices.isEmpty()) return;

        // Step 1: Export CSV
        exportService.exportArchivedInvoices(expiredInvoices);

        // Step 2: Issue Kafka DELETE_INVOICE events
        for (ArchivedInvoice invoice : expiredInvoices) {
            InvoiceEventDTO deleteEvent = InvoiceEventDTO.builder()
                    .invoiceId(invoice.getInvoiceId())
                    .customerId(invoice.getCustomerId())
                    .totalAmount(invoice.getTotalAmount())
                    .paymentStatus(PaymentStatus.valueOf(invoice.getPaymentStatus()))
                    .eventType(InvoiceEventType.DELETE_INVOICE)
                    .build();

            invoiceDeletionProducer.publish(deleteEvent);  // ✅ CHANGED: method renamed
        }

        // Step 3: Delete from archive DB
        for (ArchivedInvoice invoice : expiredInvoices) {
            archivedInvoiceItemRepository.deleteAll(invoice.getItems());
            archivedInvoiceRepository.delete(invoice);
        }
    }
}
