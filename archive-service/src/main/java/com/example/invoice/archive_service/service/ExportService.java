package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.entity.ArchivedInvoice;
import com.example.invoice.archive_service.entity.ArchivedInvoiceItem;
import com.example.invoice.common.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private static final String EXPORT_DIR = "exports/";
    private static final String[] HEADERS = {
            "Invoice ID", "Customer ID", "Name", "Email",
            "Invoice Date", "Total Amount", "Payment Status",
            "Item Description", "Quantity", "Unit Price", "Total Price"
    };

    public void exportArchivedInvoices(List<ArchivedInvoice> invoices) {
        try {
            Files.createDirectories(Paths.get(EXPORT_DIR));

            String fileName = "archived_invoices_" +
                    java.time.LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";

            try (OutputStream out = new FileOutputStream(EXPORT_DIR + fileName)) {
                List<String[]> rows = new ArrayList<>();

                for (ArchivedInvoice invoice : invoices) {
                    List<ArchivedInvoiceItem> items = invoice.getItems();
                    for (ArchivedInvoiceItem item : items) {
                        rows.add(new String[]{
                                String.valueOf(invoice.getInvoiceId()),
                                String.valueOf(invoice.getCustomerId()),
                                invoice.getName(),
                                invoice.getEmail(),
                                invoice.getInvoiceDate().toString(),
                                invoice.getTotalAmount().toPlainString(),
                                invoice.getPaymentStatus(),
                                item.getDescription(),
                                String.valueOf(item.getQuantity()),
                                item.getUnitPrice().toPlainString(),
                                item.getTotalPrice().toPlainString()
                        });
                    }
                }

                CsvUtil.writeCsv(out, HEADERS, rows);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to export archived invoices", e);
        }
    }
}