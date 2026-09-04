package com.example.invoice.import_service.service;

import com.example.invoice.common.enums.InvoiceEventType;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceItemDTO;
import com.example.invoice.common.util.HashUtil;
import com.example.invoice.import_service.entity.ImportInvoice;
import com.example.invoice.import_service.entity.ImportInvoiceItem;
import com.example.invoice.import_service.exception.ImportValidationException;
import com.example.invoice.import_service.kafka.InvoiceImportProducer;
import com.example.invoice.import_service.mapper.ImportMapper;
import com.example.invoice.import_service.repository.ImportInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persists a single invoice from its CSV rows.
 *
 * Deliberately a separate bean rather than a method on ImportService.
 * Spring applies @Transactional through a proxy, and a call from one method of
 * a class to another method of the same class never leaves the object — the
 * proxy is bypassed and the annotation silently does nothing. Crossing a bean
 * boundary is what makes REQUIRES_NEW actually take effect.
 */
@Service
@RequiredArgsConstructor
public class InvoiceImporter {

    private final ImportInvoiceRepository invoiceRepository;
    private final InvoiceImportProducer eventProducer;

    /**
     * REQUIRES_NEW so one invoice's failure rolls back only that invoice.
     * Without it, a mid-file failure leaves earlier rows committed and later
     * ones not, with no record of where it stopped.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importOne(List<CSVRecord> records) {
        String raw = records.stream().map(CSVRecord::toString).collect(Collectors.joining());
        String contentHash = HashUtil.computeSHA256(raw);

        ImportInvoice invoice = ImportMapper.toInvoice(records, new ArrayList<>(), contentHash);
        List<ImportInvoiceItem> items = records.stream()
                .map(r -> ImportMapper.toInvoiceItem(r, invoice))
                .collect(Collectors.toList());
        invoice.setItems(items);

        BigDecimal calculated = items.stream()
                .map(ImportInvoiceItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (calculated.compareTo(invoice.getTotalAmount()) != 0) {
            throw new ImportValidationException(
                    "Total amount mismatch: file says " + invoice.getTotalAmount()
                            + ", items sum to " + calculated);
        }

        invoiceRepository.save(invoice);
        eventProducer.publish(toEvent(invoice, items));
    }

    private InvoiceEventDTO toEvent(ImportInvoice invoice, List<ImportInvoiceItem> items) {
        return InvoiceEventDTO.builder()
                .invoiceId(invoice.getInvoiceId())
                .customerId(invoice.getCustomerId())
                .invoiceDate(invoice.getInvoiceDate())
                .totalAmount(invoice.getTotalAmount())
                .paymentStatus(invoice.getPaymentStatus())
                .contentHash(invoice.getContentHash())
                .eventType(InvoiceEventType.CREATED)
                .items(items.stream()
                        .map(i -> InvoiceItemDTO.builder()
                                .description(i.getDescription())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .totalPrice(i.getTotalPrice())
                                .build())
                        .toList())
                .build();
    }
}