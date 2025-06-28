package com.example.invoice.export_service.controller;

import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import com.example.invoice.export_service.service.ExportService;
import com.example.invoice.export_service.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.YearMonth;
import java.util.Optional;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final PdfExportService pdfExportService;
    private final ExportInvoiceRepository exportInvoiceRepository;

    @GetMapping("/monthly-zip")
    public ResponseEntity<Resource> downloadMonthlyZip(
            @RequestParam("month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File zipFile = exportService.generateMonthlyZip(month, tempDir);

        FileSystemResource resource = new FileSystemResource(zipFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipFile.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<Resource> downloadSingleInvoicePdf(@PathVariable Long invoiceId) throws Exception {
        Optional<ExportInvoice> optionalInvoice = exportInvoiceRepository.findById(invoiceId);
        if (optionalInvoice.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File pdfFile = pdfExportService.generatePdfForInvoice(optionalInvoice.get(), tempDir);

        FileSystemResource resource = new FileSystemResource(pdfFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + pdfFile.getName())
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}