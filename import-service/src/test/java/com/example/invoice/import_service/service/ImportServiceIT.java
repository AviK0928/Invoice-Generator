package com.example.invoice.import_service.service;

import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.import_service.IntegrationTest;
import com.example.invoice.import_service.dto.ImportSummaryDTO;
import com.example.invoice.import_service.exception.ImportValidationException;
import com.example.invoice.import_service.repository.ImportInvoiceItemRepository;
import com.example.invoice.import_service.repository.ImportInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import com.example.invoice.import_service.exception.ImportValidationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Import isolation: one bad invoice in a file must not discard the good ones.
 *
 * The bad invoice fails on quantity, not on the total-amount check. That is
 * deliberate — the total check throws before invoiceRepository.save(...), so a
 * file failing that way exercises no rollback at all and would pass with the
 * transaction annotation deleted. quantity = 0 passes the total check, gets
 * saved. Its first item inserts, which forces the parent invoice's insert; the
 * second fails @Min(1) during the cascade. So rows genuinely exist in the
 * transaction when it rolls back — a single bad row would throw before any
 * INSERT reached Postgres and would prove nothing about rollback.
 */
class ImportServiceIT extends IntegrationTest {

    @Autowired
    ImportService importService;
    @Autowired
    ImportInvoiceRepository invoiceRepository;
    @Autowired
    ImportInvoiceItemRepository itemRepository;
    @Autowired
    OutboxEventRepository outboxRepository;

    @BeforeEach
    void clean() {
        invoiceRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("a failed invoice is rolled back alone; the rest of the file imports")
    void oneBadInvoiceDoesNotDiscardTheFile() {
        ImportSummaryDTO summary = importService.importInvoiceData(
                csv("""
                        invoiceId,customerId,name,email,invoiceDate,paymentStatus,totalAmount,description,quantity,unitPrice,totalPrice
                        1001,1,Test Co,test@example.com,2026-09-05,PENDING,30.00,Widget,2,10.00,20.00
                        1001,1,Test Co,test@example.com,2026-09-05,PENDING,30.00,Gadget,1,10.00,10.00
                        1002,1,Test Co,test@example.com,2026-09-05,PENDING,30.00,Good item,1,10.00,10.00
                        1002,1,Test Co,test@example.com,2026-09-05,PENDING,30.00,Broken,0,10.00,20.00
                        """));

        assertThat(summary.getTotalRows()).isEqualTo(4);
        assertThat(summary.getSuccessCount()).isOne();
        assertThat(summary.getFailureCount()).isOne();
        assertThat(summary.getErrors()).singleElement().asString().contains("invoiceId 1002");

        // The good invoice and both its items survived.
        assertThat(invoiceRepository.findAll())
                .singleElement()
                .satisfies(i -> assertThat(i.getInvoiceId()).isEqualTo(1001L));
        assertThat(itemRepository.count()).isEqualTo(2);

        // The sharpest assertion: the outbox row commits in the importer's
        // transaction, so an event for 1002 would mean an event announcing an
        // invoice that does not exist — the exact failure the outbox prevents.
        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> assertThat(e.getAggregateId()).isEqualTo("1001"));
    }

    @Test
    @DisplayName("a total-amount mismatch fails that invoice only")
    void totalMismatchFailsOneInvoice() {
        ImportSummaryDTO summary = importService.importInvoiceData(
                csv("""
                        invoiceId,customerId,name,email,invoiceDate,paymentStatus,totalAmount,description,quantity,unitPrice,totalPrice
                        2001,1,Test Co,test@example.com,2026-09-05,PENDING,20.00,Widget,2,10.00,20.00
                        2002,1,Test Co,test@example.com,2026-09-05,PENDING,99.00,Widget,1,10.00,10.00
                        """));

        assertThat(summary.getSuccessCount()).isOne();
        assertThat(summary.getFailureCount()).isOne();
        assertThat(summary.getErrors()).singleElement().asString()
                .contains("invoiceId 2002", "Total amount mismatch");

        assertThat(invoiceRepository.findAll())
                .singleElement()
                .satisfies(i -> assertThat(i.getInvoiceId()).isEqualTo(2001L));
    }

    @Test
    @DisplayName("an unparseable invoiceId rejects the whole file")
    void unparseableInvoiceIdRejectsTheFile() {
        assertThatThrownBy(() -> importService.importInvoiceData(
                csv("""
                        invoiceId,customerId,name,email,invoiceDate,paymentStatus,totalAmount,description,quantity,unitPrice,totalPrice
                        3001,1,Test Co,test@example.com,2026-09-05,PENDING,20.00,Widget,2,10.00,20.00
                        NOT_A_NUMBER,1,Test Co,test@example.com,2026-09-05,PENDING,20.00,Widget,2,10.00,20.00
                        """)))
                .isInstanceOf(ImportValidationException.class)
                .hasMessageContaining("invalid or missing invoiceId");

        // Grouping happens before any import, so a malformed id rejects the file
        // rather than importing the rows that parsed. Different from the
        // per-invoice failures above, and deliberate.
        assertThat(invoiceRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    // ------------------------------------------------------------- fixtures

    private MultipartFile csv(String content) {
        return new MockMultipartFile("file", "invoices.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }
}