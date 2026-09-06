package com.example.invoice.export_service.service;

import com.example.invoice.common.util.CsvUtil;
import com.example.invoice.export_service.entity.ExportCustomer;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import com.example.invoice.export_service.exception.ExportTooLargeException;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    /**
     * Cap on invoices per export. The archive is assembled in memory (see
     * below), so this bounds worst-case heap use on a 512MB instance.
     */
    @Value("${export.max-invoices:500}")
    private int maxInvoices;
    private final ExportInvoiceRepository exportInvoiceRepository;
    private final ExportInvoiceItemRepository exportInvoiceItemRepository;
    private final PdfExportService pdfExportService;

    /**
     * Builds the monthly archive entirely in memory and returns the bytes.
     *
     * Deliberately NOT streamed to the response. StreamingResponseBody writes
     * after the controller returns, which is outside the transaction — and with
     * open-in-view disabled, touching invoice.getItems() there throws
     * LazyInitializationException. Assembling inside the transaction is the
     * correct trade for this data volume; a true streaming version would need
     * the whole graph projected into DTOs up front.
     *
     * Previously this wrote PDFs and a CSV into java.io.tmpdir, zipped from
     * disk, and never deleted anything.
     */
    @Transactional(readOnly = true)
    public byte[] generateMonthlyZip(YearMonth month) throws Exception {
        List<ExportInvoice> invoices = exportInvoiceRepository
                .findByInvoiceDateBetween(month.atDay(1), month.atEndOfMonth());

        if (invoices.size() > maxInvoices) {
            throw new ExportTooLargeException(month, invoices.size(), maxInvoices);
        }

        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
            for (ExportInvoice invoice : invoices) {
                zip.putNextEntry(new ZipEntry("invoice_" + invoice.getInvoiceId() + ".pdf"));
                zip.write(pdfExportService.generatePdfForInvoice(invoice));
                zip.closeEntry();
            }

            zip.putNextEntry(new ZipEntry("invoices_" + month + ".csv"));
            zip.write(buildCsv(invoices));
            zip.closeEntry();
        }

        return zipBytes.toByteArray();
    }

    private byte[] buildCsv(List<ExportInvoice> invoices) throws Exception {
        String[] headers = { "Invoice ID", "Customer ID", "Customer Name", "Customer Email",
                "Total Amount", "Payment Status", "Item Description", "Quantity",
                "Unit Price", "Total Price" };

        List<String[]> rows = new ArrayList<>();
        for (ExportInvoice invoice : invoices) {
            // customer is a read-only join on customerId, and
            // fk_export_invoices_customer means an invoice cannot be persisted
            // without one — so this is unreachable while that constraint
            // stands. Kept as a null-safe read rather than three chained
            // dereferences: if the FK were ever relaxed, one inconsistent
            // invoice would take the whole month's export down with an NPE.
            ExportCustomer customer = invoice.getCustomer();
            if (customer == null) {
                log.warn("Invoice {} has no projected customer {}; exporting with blank customer fields",
                        invoice.getInvoiceId(), invoice.getCustomerId());
            }

            for (ExportInvoiceItem item : exportInvoiceItemRepository.findAllByInvoice(invoice)) {
                rows.add(new String[] {
                        invoice.getInvoiceId().toString(),
                        invoice.getCustomerId().toString(),
                        customer != null ? customer.getName() : null,
                        customer != null ? customer.getEmail() : null,
                        invoice.getTotalAmount().toPlainString(),
                        invoice.getPaymentStatus().name(),
                        item.getDescription(),
                        item.getQuantity().toString(),
                        item.getUnitPrice().toPlainString(),
                        item.getTotalPrice().toPlainString()
                });
            }
        }

        // Written to a buffer rather than straight into the ZipOutputStream:
        // CsvUtil may close the stream it is handed, which would end the archive.
        ByteArrayOutputStream csv = new ByteArrayOutputStream();
        CsvUtil.writeCsv(csv, headers, rows);
        return csv.toByteArray();
    }
}