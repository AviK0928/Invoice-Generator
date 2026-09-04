package com.example.invoice.archive_service.controller;

import com.example.invoice.archive_service.dto.ArchiveResponseDTO;
import com.example.invoice.archive_service.dto.UnarchiveRequestDTO;
import com.example.invoice.archive_service.exception.ArchivedInvoiceNotFoundException;
import com.example.invoice.archive_service.service.ArchiveService;
import com.example.invoice.archive_service.service.UnarchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/archives")
@RequiredArgsConstructor
public class ArchiveController {

    private final UnarchiveService unarchiveService;
    private final ArchiveService archiveService;

    @GetMapping
    public Page<ArchiveResponseDTO> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "archivedInvoiceId", direction = Sort.Direction.DESC) Pageable pageable) {
        return archiveService.listArchived(customerId, fromDate, toDate, pageable);
    }

    @GetMapping("/{invoiceId}")
    public ArchiveResponseDTO get(@PathVariable Long invoiceId) {
        return archiveService.getArchivedInvoice(invoiceId);
    }

    @PostMapping("/unarchive")
    public ResponseEntity<Void> unarchive(@Valid @RequestBody UnarchiveRequestDTO request) {
        if (!archiveService.existsByInvoiceId(request.getInvoiceId())) {
            throw new ArchivedInvoiceNotFoundException(request.getInvoiceId());
        }
        unarchiveService.unarchiveInvoice(request.getInvoiceId());
        return ResponseEntity.noContent().build();
    }
}