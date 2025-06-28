package com.example.invoice.export_service.service;


import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.common.util.CsvUtil;
import com.example.invoice.common.util.HashUtil;
import com.example.invoice.common.util.PdfUtil;
import com.example.invoice.common.util.ZipUtil;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ExportInvoiceRepository exportInvoiceRepository;
    private final ExportInvoiceItemRepository exportInvoiceItemRepository;

    public File generateMonthlyZip(YearMonth month, File outputDir) throws Exception {
        List<ExportInvoice> invoiceList = exportInvoiceRepository.findByInvoiceDateBetween(
                month.atDay(1), month.atEndOfMonth());

        File tempDir = new File(outputDir, "export_" + month);
        tempDir.mkdirs();

        List<File> generatedFiles = new ArrayList<>();

        // PDF Generation
        for (ExportInvoice invoice : invoiceList) {
            InvoiceEventDTO dto = InvoiceEventDTO.builder()
                    .invoiceId(invoice.getInvoiceId())
                    .customerId(invoice.getCustomer().getCustomerId())
                    .totalAmount(invoice.getTotalAmount())
                    .paymentStatus(invoice.getPaymentStatus())
                    .eventType(null) // not relevant for static export
                    .build();

            byte[] pdfBytes = PdfUtil.generateInvoicePdf(dto);
            File pdfFile = new File(tempDir, "invoice_" + invoice.getInvoiceId() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                fos.write(pdfBytes);
            }
            generatedFiles.add(pdfFile);
        }

        // CSV Generation
        String[] headers = {"Invoice ID", "Customer ID", "Customer Name", "Customer Email",
                "Total Amount", "Payment Status", "Item Description", "Quantity", "Unit Price", "Total Price"};
        List<String[]> rows = new ArrayList<>();

        for (ExportInvoice invoice : invoiceList) {
            List<ExportInvoiceItem> items = exportInvoiceItemRepository.findAllByInvoice(invoice);
            for (ExportInvoiceItem item : items) {
                rows.add(new String[]{
                        invoice.getInvoiceId().toString(),
                        invoice.getCustomer().getCustomerId().toString(),
                        invoice.getCustomer().getName(),
                        invoice.getCustomer().getEmail(),
                        invoice.getTotalAmount().toPlainString(),
                        invoice.getPaymentStatus().name(),
                        item.getDescription(),
                        item.getQuantity().toString(),
                        item.getUnitPrice().toPlainString(),
                        item.getTotalPrice().toPlainString()
                });
            }
        }

        File csvFile = new File(tempDir, "invoices_" + month + ".csv");
        try (FileOutputStream csvOut = new FileOutputStream(csvFile)) {
            CsvUtil.writeCsv(csvOut, headers, rows);
        }
        generatedFiles.add(csvFile);

        // ZIP Generation
        File zipFile = new File(outputDir, "invoice_export_" + month + ".zip");
        try (FileOutputStream zipOut = new FileOutputStream(zipFile)) {
            ZipUtil.zipFiles(generatedFiles, zipOut);
        }

        return zipFile;
    }
    public File generateSystemBackupCsv(File outputDir) throws Exception {
        List<ExportInvoice> invoiceList = exportInvoiceRepository.findAll();
        String[] headers = {"Invoice ID", "Customer ID", "Customer Name", "Customer Email",
                "Total Amount", "Payment Status", "Item Description", "Quantity", "Unit Price", "Total Price", "Hash"};

        List<String[]> rows = new ArrayList<>();

        for (ExportInvoice invoice : invoiceList) {
            List<ExportInvoiceItem> items = exportInvoiceItemRepository.findAllByInvoice(invoice);

            for (ExportInvoiceItem item : items) {
                String rowData = invoice.getInvoiceId() + "|" + item.getDescription() + "|" + item.getQuantity();
                String hash = HashUtil.computeSHA256(rowData);

                rows.add(new String[]{
                        invoice.getInvoiceId().toString(),
                        invoice.getCustomer().getCustomerId().toString(),
                        invoice.getCustomer().getName(),
                        invoice.getCustomer().getEmail(),
                        invoice.getTotalAmount().toPlainString(),
                        invoice.getPaymentStatus().name(),
                        item.getDescription(),
                        item.getQuantity().toString(),
                        item.getUnitPrice().toPlainString(),
                        item.getTotalPrice().toPlainString(),
                        hash
                });
            }
        }

        File csvFile = new File(outputDir, "system_backup_" + System.currentTimeMillis() + ".csv");
        try (FileOutputStream out = new FileOutputStream(csvFile)) {
            CsvUtil.writeCsv(out, headers, rows);
        }
        return csvFile;
    }
}