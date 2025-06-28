package com.example.invoice.export_service.config;

import com.example.invoice.export_service.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final ExportService exportService;

    @Scheduled(cron = "0 0 0 * * *") // Every day at midnight
    public void performSystemBackup() {
        try {
            File backupDir = new File("backups");
            backupDir.mkdirs();
            File csvBackup = exportService.generateSystemBackupCsv(backupDir);
            log.info("System backup created: {}", csvBackup.getAbsolutePath());
        } catch (Exception e) {
            log.error("Error creating system CSV backup", e);
        }
    }
}
