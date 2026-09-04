package com.example.invoice.import_service.controller;

import com.example.invoice.import_service.dto.ImportSummaryDTO;
import com.example.invoice.import_service.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/invoices")
    public ImportSummaryDTO importInvoices(@RequestParam("file") MultipartFile file) {
        return importService.importInvoiceData(file);
    }
}