package com.example.invoice.archive_service.controller;

import com.example.invoice.archive_service.dto.ArchiveResponseDTO;
import com.example.invoice.archive_service.dto.UnarchiveRequestDTO;
import com.example.invoice.archive_service.service.ArchiveService;
import com.example.invoice.archive_service.service.UnarchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/archives")
@RequiredArgsConstructor
public class ArchiveController {

    private final UnarchiveService unarchiveService;
    private final ArchiveService archiveService;

    @PostMapping("/unarchive")
    public ResponseEntity<String> unarchiveInvoice(@Valid @RequestBody UnarchiveRequestDTO request) {
        if (!archiveService.existsByInvoiceId(request.getInvoiceId())) {
            return ResponseEntity.badRequest().body("Invoice not found in archive.");
        }

        unarchiveService.unarchiveInvoice(request.getInvoiceId());
        return ResponseEntity.ok("Invoice unarchived successfully.");
    }

    @GetMapping("/check/{invoiceId}")
    public ResponseEntity<ArchiveResponseDTO> checkArchivedInvoice(@PathVariable Long invoiceId) {
        return archiveService.getArchivedInvoiceDetails(invoiceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}