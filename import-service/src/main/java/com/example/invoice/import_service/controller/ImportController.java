package com.example.invoice.import_service.controller;

import com.example.invoice.import_service.dto.ImportSummaryDTO;
import com.example.invoice.import_service.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/invoices")
    public ResponseEntity<?> importInvoices(@RequestParam("file") MultipartFile file) {
        try {
            ImportSummaryDTO summary = importService.importInvoiceData(file);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unexpected error occurred");
        }
    }
}