package com.example.invoice.export_service.service;

import com.example.invoice.common.util.CsvUtil;
import com.example.invoice.export_service.entity.ExportCustomer;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import com.example.invoice.export_service.repository.ExportCustomerRepository;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CsvExportService {

    private final ExportCustomerRepository customerRepository;
    private final ExportInvoiceRepository invoiceRepository;
    private final ExportInvoiceItemRepository itemRepository;

    /**
     * Generate CSVs of all data for system backup.
     * @param outputDir directory to save CSVs
     * @throws Exception
     */
    public void generateSystemBackupCsvs(File outputDir) throws Exception {
        generateCustomerCsv(outputDir);
        generateInvoiceCsv(outputDir);
        generateInvoiceItemCsv(outputDir);
    }

    private void generateCustomerCsv(File outputDir) throws Exception {
        List<ExportCustomer> customers = customerRepository.findAll();
        File file = new File(outputDir, "customers.csv");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"customerId", "name", "email"});
            for (ExportCustomer c : customers) {
                rows.add(new String[]{c.getCustomerId().toString(), c.getName(), c.getEmail()});
            }
            CsvUtil.writeCsv(fos, rows.get(0), rows.subList(1, rows.size()));
        }
    }

    private void generateInvoiceCsv(File outputDir) throws Exception {
        List<ExportInvoice> invoices = invoiceRepository.findAll();
        File file = new File(outputDir, "invoices.csv");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"invoiceId", "customerId", "invoiceDate", "totalAmount", "paymentStatus", "archived", "createdAt", "contentHash"});
            for (ExportInvoice inv : invoices) {
                rows.add(new String[]{
                        inv.getInvoiceId().toString(),
                        inv.getCustomerId().toString(),
                        inv.getInvoiceDate().toString(),
                        inv.getTotalAmount().toString(),
                        inv.getPaymentStatus().name(),
                        inv.getArchived().toString(),
                        inv.getCreatedAt().toString(),
                        inv.getContentHash()
                });
            }
            CsvUtil.writeCsv(fos, rows.get(0), rows.subList(1, rows.size()));
        }
    }

    private void generateInvoiceItemCsv(File outputDir) throws Exception {
        List<ExportInvoiceItem> items = itemRepository.findAll();
        File file = new File(outputDir, "invoice_items.csv");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"itemId", "invoiceId", "description", "quantity", "unitPrice", "totalPrice"});
            for (ExportInvoiceItem item : items) {
                rows.add(new String[]{
                        item.getItemId().toString(),
                        item.getInvoice().getInvoiceId().toString(),
                        item.getDescription(),
                        item.getQuantity().toString(),
                        item.getUnitPrice().toString(),
                        item.getTotalPrice().toString()
                });
            }
            CsvUtil.writeCsv(fos, rows.get(0), rows.subList(1, rows.size()));
        }
    }

    /**
     * Generates CSV files for invoices within the specified month for user requested ZIP.
     * @param month target month
     * @param outputDir output directory
     * @return list of generated CSV files
     * @throws Exception
     */
    public List<File> generateCsvsForMonth(YearMonth month, File outputDir) throws Exception {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        // Customers for that month (assumed all customers; can be filtered if needed)
        List<ExportCustomer> customers = customerRepository.findAll();

        List<File> csvFiles = new ArrayList<>();

        // Customers CSV
        File customerCsv = new File(outputDir, "customers_" + month + ".csv");
        try (FileOutputStream fos = new FileOutputStream(customerCsv)) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"customerId", "name", "email"});
            for (ExportCustomer c : customers) {
                rows.add(new String[]{c.getCustomerId().toString(), c.getName(), c.getEmail()});
            }
            CsvUtil.writeCsv(fos, rows.get(0), rows.subList(1, rows.size()));
        }
        csvFiles.add(customerCsv);

        // Invoice CSV
        List<ExportInvoice> invoices = invoiceRepository.findByInvoiceDateBetween(start, end);
        File invoiceCsv = new File(outputDir, "invoices_" + month + ".csv");
        try (FileOutputStream fos = new FileOutputStream(invoiceCsv)) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"invoiceId", "customerId", "invoiceDate", "totalAmount", "paymentStatus", "archived", "createdAt", "contentHash"});
            for (ExportInvoice inv : invoices) {
                rows.add(new String[]{
                        inv.getInvoiceId().toString(),
                        inv.getCustomerId().toString(),
                        inv.getInvoiceDate().toString(),
                        inv.getTotalAmount().toString(),
                        inv.getPaymentStatus().name(),
                        inv.getArchived().toString(),
                        inv.getCreatedAt().toString(),
                        inv.getContentHash()
                });
            }
            CsvUtil.writeCsv(fos, rows.get(0), rows.subList(1, rows.size()));
        }
        csvFiles.add(invoiceCsv);

        // Invoice Items CSV
        List<ExportInvoiceItem> items = itemRepository.findAll(); // filter by month if needed
        File itemsCsv = new File(outputDir, "invoice_items_" + month + ".csv");
        try (FileOutputStream fos = new FileOutputStream(itemsCsv)) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"itemId", "invoiceId", "description", "quantity", "unitPrice", "totalPrice"});
            for (ExportInvoiceItem item : items) {
                // include only items related to invoices in the given month
                if (item.getInvoice().getInvoiceDate().compareTo(start) >= 0 && item.getInvoice().getInvoiceDate().compareTo(end) <= 0) {
                    rows.add(new String[]{
                            item.getItemId().toString(),
                            item.getInvoice().getInvoiceId().toString(),
                            item.getDescription(),
                            item.getQuantity().toString(),
                            item.getUnitPrice().toString(),
                            item.getTotalPrice().toString()
                    });
                }
            }
            CsvUtil.writeCsv(fos, rows.get(0), rows.subList(1, rows.size()));
        }
        csvFiles.add(itemsCsv);

        return csvFiles;
    }
}