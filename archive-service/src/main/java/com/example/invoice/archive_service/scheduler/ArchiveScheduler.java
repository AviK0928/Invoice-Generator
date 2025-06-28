package com.example.invoice.archive_service.scheduler;

import com.example.invoice.archive_service.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArchiveScheduler {

    private final ArchiveService archiveService;

    @Scheduled(cron = "0 0 0 1 */6 *") // Every 6 months, on the 1st day at midnight
    public void purgeArchivedInvoices() {
        log.info("Starting scheduled purge of old archived invoices...");

        archiveService.exportAndDeleteExpiredArchives();

        log.info("Scheduled purge completed.");
    }
}
