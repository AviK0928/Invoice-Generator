package com.example.invoice.import_service.service;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.common.util.HashUtil;
import com.example.invoice.import_service.dto.ImportSummaryDTO;
import com.example.invoice.import_service.entity.ImportInvoice;
import com.example.invoice.import_service.entity.ImportInvoiceItem;
import com.example.invoice.import_service.kafka.InvoiceImportProducer;
import com.example.invoice.import_service.mapper.ImportMapper;
import com.example.invoice.import_service.repository.ImportInvoiceItemRepository;
import com.example.invoice.import_service.repository.ImportInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportInvoiceRepository invoiceRepository;
    private final ImportInvoiceItemRepository itemRepository;
    private final InvoiceImportProducer eventProducer;

    public ImportSummaryDTO importInvoiceData(MultipartFile file) throws Exception {
        List<CSVRecord> records = CSVParser.parse(
                new InputStreamReader(file.getInputStream()),
                CSVFormat.DEFAULT.withFirstRecordAsHeader()
        ).getRecords();

        if (records.isEmpty()) throw new IllegalArgumentException("Empty file");

        // Group by invoiceId
        Map<Long, List<CSVRecord>> invoiceGroups = records.stream()
                .collect(Collectors.groupingBy(r -> Long.parseLong(r.get("invoiceId"))));

        int success = 0;
        for (Map.Entry<Long, List<CSVRecord>> entry : invoiceGroups.entrySet()) {
            List<CSVRecord> invoiceRecords = entry.getValue();
            try {
                // Compute content hash
                String raw = invoiceRecords.stream()
                        .map(CSVRecord::toString)
                        .collect(Collectors.joining());
                String contentHash = HashUtil.computeSHA256(raw);

                // Convert records to entity
                ImportInvoice invoice = ImportMapper.toInvoice(
                        invoiceRecords,
                        new ArrayList<>(), // set below
                        contentHash
                );

                List<ImportInvoiceItem> items = invoiceRecords.stream()
                        .map(r -> ImportMapper.toInvoiceItem(r, invoice))
                        .collect(Collectors.toList());
                invoice.setItems(items);

                // Validate totalAmount
                BigDecimal calculated = items.stream()
                        .map(ImportInvoiceItem::getTotalPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (calculated.compareTo(invoice.getTotalAmount()) != 0) {
                    throw new IllegalArgumentException("Mismatch in total amount for invoiceId: " + invoice.getInvoiceId());
                }

                // Save locally
                invoiceRepository.save(invoice);

                // Build and send event
                InvoiceEventDTO event = InvoiceEventDTO.builder()
                        .invoiceId(invoice.getInvoiceId())
                        .customerId(invoice.getCustomerId())
                        .totalAmount(invoice.getTotalAmount())
                        .paymentStatus(invoice.getPaymentStatus())
                        .eventType(com.example.invoice.common.enums.InvoiceEventType.CREATED)
                        .build();

                //eventProducer.publish(event);
                success++;
            } catch (Exception e) {
                throw new IllegalArgumentException("Error at invoiceId " + entry.getKey() + ": " + e.getMessage());
            }
        }

        return ImportSummaryDTO.builder()
                .totalRows(records.size())
                .successCount(success)
                .failureCount(0)
                .message("Import completed successfully")
                .build();
    }
}