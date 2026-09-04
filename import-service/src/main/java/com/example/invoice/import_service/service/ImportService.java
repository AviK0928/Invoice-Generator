package com.example.invoice.import_service.service;

import com.example.invoice.import_service.dto.ImportSummaryDTO;
import com.example.invoice.import_service.exception.ImportValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

        private static final Set<String> ALLOWED_TYPES = Set.of(
                        "text/csv", "application/csv", "text/plain", "application/vnd.ms-excel");

        private final InvoiceImporter invoiceImporter;

        /** Row cap is an operational knob, not a compile-time decision. */
        @Value("${import.max-rows:10000}")
        private int maxRows;

        public ImportSummaryDTO importInvoiceData(MultipartFile file) {
                validateUpload(file);
                Map<Long, List<CSVRecord>> groups = parseAndGroup(file);

                int success = 0;
                List<String> errors = new ArrayList<>();

                for (Map.Entry<Long, List<CSVRecord>> entry : groups.entrySet()) {
                        try {
                                invoiceImporter.importOne(entry.getValue());
                                success++;
                        } catch (Exception e) {
                                // One bad invoice must not discard the rest of the file.
                                log.warn("Import failed for invoiceId {}", entry.getKey(), e);
                                errors.add("invoiceId " + entry.getKey() + ": " + e.getMessage());
                        }
                }

                int totalRows = groups.values().stream().mapToInt(List::size).sum();
                return ImportSummaryDTO.builder()
                                .totalRows(totalRows)
                                .successCount(success)
                                .failureCount(errors.size())
                                .errors(errors)
                                .message(errors.isEmpty()
                                                ? "Import completed successfully"
                                                : "Import completed with " + errors.size() + " failure(s)")
                                .build();
        }

        private void validateUpload(MultipartFile file) {
                if (file == null || file.isEmpty()) {
                        throw new ImportValidationException("File is empty.");
                }

                String name = file.getOriginalFilename();
                if (name == null || !name.toLowerCase().endsWith(".csv")) {
                        throw new ImportValidationException("Only .csv files are accepted.");
                }

                // Content-Type is set by the client and is not trustworthy — curl sends
                // application/octet-stream by default, as do many real clients. Logged,
                // not enforced; the extension check above and the parser are the real
                // gates.
                String contentType = file.getContentType();
                if (contentType != null && !ALLOWED_TYPES.contains(contentType)) {
                        log.debug("Unexpected content type {} for {}", contentType, name);
                }
        }

        /**
         * Iterates the parser rather than calling getRecords(), so the row cap is
         * enforced as rows arrive instead of after the whole file is in heap.
         */
        private Map<Long, List<CSVRecord>> parseAndGroup(MultipartFile file) {
                Map<Long, List<CSVRecord>> groups = new LinkedHashMap<>();
                int rows = 0;

                try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                                CSVParser parser = CSVFormat.DEFAULT.builder()
                                                .setHeader().setSkipHeaderRecord(true).setTrim(true)
                                                .build().parse(reader)) {

                        for (CSVRecord record : parser) {
                                if (++rows > maxRows) {
                                        throw new ImportValidationException(
                                                        "File exceeds the maximum of " + maxRows + " rows.");
                                }
                                long invoiceId;
                                try {
                                        invoiceId = Long.parseLong(record.get("invoiceId"));
                                } catch (IllegalArgumentException e) {
                                        throw new ImportValidationException(
                                                        "Row " + record.getRecordNumber()
                                                                        + ": invalid or missing invoiceId.");
                                }
                                groups.computeIfAbsent(invoiceId, k -> new ArrayList<>()).add(record);
                        }
                } catch (ImportValidationException e) {
                        throw e;
                } catch (Exception e) {
                        throw new ImportValidationException("Could not parse CSV: " + e.getMessage());
                }

                if (groups.isEmpty()) {
                        throw new ImportValidationException("File contains no data rows.");
                }
                return groups;
        }
}