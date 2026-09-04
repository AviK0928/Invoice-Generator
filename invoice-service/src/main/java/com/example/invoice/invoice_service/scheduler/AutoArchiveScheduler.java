package com.example.invoice.invoice_service.scheduler;

import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.invoice_service.entity.Invoice;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Archives invoices older than the retention window.
 *
 * Delegates to InvoiceService rather than duplicating the archive logic. The
 * previous version built its own event and published only to invoice-archived,
 * so export-service never learned that an auto-archived invoice had been
 * archived.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoArchiveScheduler {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;

    @Value("${invoice.auto-archive-after-months:6}")
    private int archiveAfterMonths;

    @Scheduled(cron = "${invoice.auto-archive-cron:0 0 2 * * *}")
    @Transactional(readOnly = true)
    public void autoArchiveInvoices() {
        LocalDate cutoff = LocalDate.now().minusMonths(archiveAfterMonths);
        List<Invoice> due = invoiceRepository.findByArchivedFalseAndInvoiceDateBefore(cutoff);

        if (due.isEmpty()) {
            return;
        }
        log.info("Auto-archiving {} invoices older than {}", due.size(), cutoff);

        int archived = 0;
        for (Invoice invoice : due) {
            try {
                // Separate bean, so each call gets its own transaction. One
                // failure must not roll back the whole run.
                invoiceService.archiveInvoice(invoice.getInvoiceId(),
                        ArchiveEventType.AUTO_ARCHIVE);
                archived++;
            } catch (Exception e) {
                log.warn("Auto-archive failed for invoice {}", invoice.getInvoiceId(), e);
            }
        }
        log.info("Auto-archived {} of {} invoices", archived, due.size());
    }
}