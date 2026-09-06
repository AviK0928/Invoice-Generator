package com.example.invoice.export_service.service;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.export_service.IntegrationTest;
import com.example.invoice.export_service.entity.ExportCustomer;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import com.example.invoice.export_service.exception.ExportTooLargeException;
import com.example.invoice.export_service.repository.ExportCustomerRepository;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The monthly archive, opened and inspected rather than weighed.
 *
 * Asserting that the ZIP is non-empty would pass against an archive of empty
 * PDFs, so these read the entry names, check the PDF magic bytes, and parse the
 * CSV text. The CSV assertions double as proof that the read-only join from
 * ExportInvoice to ExportCustomer resolves — buildCsv dereferences
 * getCustomer() without a null check, so a missing customer row is a 500.
 */
class ExportServiceIT extends IntegrationTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    @Autowired
    ExportService exportService;
    @Autowired
    ExportInvoiceRepository invoiceRepository;
    @Autowired
    ExportInvoiceItemRepository itemRepository;
    @Autowired
    ExportCustomerRepository customerRepository;

    @BeforeEach
    void clean() {
        itemRepository.deleteAll();
        invoiceRepository.deleteAll();
        customerRepository.deleteAll();
        customerRepository.save(ExportCustomer.builder()
                .customerId(1L).name("Test Co").email("test@example.com").build());
    }

    @Test
    @DisplayName("the archive holds one PDF per invoice plus a single CSV")
    void archiveStructure() throws Exception {
        seedInvoice(1001L, MONTH.atDay(3));
        seedInvoice(1002L, MONTH.atDay(20));

        Map<String, byte[]> entries = unzip(exportService.generateMonthlyZip(MONTH));

        assertThat(entries.keySet()).containsExactlyInAnyOrder(
                "invoice_1001.pdf", "invoice_1002.pdf", "invoices_2026-09.csv");
    }

    @Test
    @DisplayName("the PDF entries are structurally PDFs, not empty files")
    void pdfEntriesAreRealPdfs() throws Exception {
        seedInvoice(1001L, MONTH.atDay(3));

        byte[] pdf = unzip(exportService.generateMonthlyZip(MONTH)).get("invoice_1001.pdf");

        // %PDF- header and %%EOF trailer. Cheap, but it distinguishes a real
        // document from the zero-length file that a length assertion accepts.
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("%%EOF");
    }

    @Test
    @DisplayName("the CSV carries the customer joined from a separate table")
    void csvResolvesTheCustomerJoin() throws Exception {
        seedInvoice(1001L, MONTH.atDay(3));

        String csv = new String(
                unzip(exportService.generateMonthlyZip(MONTH)).get("invoices_2026-09.csv"),
                StandardCharsets.UTF_8);

        assertThat(csv).contains("Invoice ID", "Customer Email");
        // ExportInvoice.customer is a read-only join on customerId. These two
        // values only appear if it resolved; a missing row NPEs in buildCsv.
        assertThat(csv).contains("Test Co", "test@example.com");
        assertThat(csv).contains("Widget", "PENDING");
    }

    @Test
    @DisplayName("invoices outside the month are not in the archive")
    void monthBoundaryExcludesNeighbours() throws Exception {
        seedInvoice(1001L, MONTH.atDay(1));
        seedInvoice(1002L, MONTH.atEndOfMonth());
        seedInvoice(2001L, MONTH.atEndOfMonth().plusDays(1));
        seedInvoice(2002L, MONTH.atDay(1).minusDays(1));

        Map<String, byte[]> entries = unzip(exportService.generateMonthlyZip(MONTH));

        // The range is inclusive at both ends, so the first and last day belong
        // and the days either side do not.
        assertThat(entries.keySet())
                .contains("invoice_1001.pdf", "invoice_1002.pdf")
                .doesNotContain("invoice_2001.pdf", "invoice_2002.pdf");
    }

    @Test
    @DisplayName("an export over the cap is refused rather than assembled")
    void overTheCapIsRefused() {
        seedInvoice(1001L, MONTH.atDay(1));
        seedInvoice(1002L, MONTH.atDay(2));
        seedInvoice(1003L, MONTH.atDay(3));

        // export.max-invoices is 2 in the test profile. The archive is built
        // in memory, so this guard is what stands between a large month and an
        // OOM on a 512MB instance.
        assertThatThrownBy(() -> exportService.generateMonthlyZip(MONTH))
                .isInstanceOf(ExportTooLargeException.class)
                .hasMessageContaining("exceeds the limit of 2");
    }

    // ------------------------------------------------------------- helpers

    private Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private void seedInvoice(Long invoiceId, LocalDate date) {
        ExportInvoice invoice = invoiceRepository.save(ExportInvoice.builder()
                .invoiceId(invoiceId)
                .customerId(1L)
                .invoiceDate(date)
                .totalAmount(new BigDecimal("20.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .archived(false)
                .build());

        List<ExportInvoiceItem> items = new ArrayList<>();
        items.add(ExportInvoiceItem.builder()
                .description("Widget")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .totalPrice(new BigDecimal("20.00"))
                .invoice(invoice)
                .build());
        itemRepository.saveAll(items);
    }
}