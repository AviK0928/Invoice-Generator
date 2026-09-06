package com.example.invoice.invoice_service.scheduler;

import com.example.invoice.invoice_service.service.PdfRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin on purpose: the work is in PdfRequestService so tests can call it
 * without waiting for a schedule. That @Scheduled fires is Spring's job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfRequestSweeper {

    private final PdfRequestService pdfRequestService;

    @Scheduled(cron = "${pdf.sweep-cron:0 */5 * * * *}")
    public void sweep() {
        int failed = pdfRequestService.failStaleRequests();
        if (failed > 0) {
            log.info("Marked {} stale PDF request(s) FAILED", failed);
        }
    }
}