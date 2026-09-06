package com.example.invoice.export_service.scheduler;

import com.example.invoice.export_service.service.PdfDocumentCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfDocumentCleanupScheduler {

    private final PdfDocumentCleanupService cleanupService;

    @Scheduled(cron = "${pdf.cleanup-cron:0 30 3 * * *}")
    public void cleanup() {
        cleanupService.deleteExpired();
    }
}