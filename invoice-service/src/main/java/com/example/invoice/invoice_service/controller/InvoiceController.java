package com.example.invoice.invoice_service.controller;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.dto.InvoiceResponseDTO;
import com.example.invoice.invoice_service.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<InvoiceResponseDTO> createInvoice(
            @RequestBody @Valid InvoiceRequestDTO dto,
            UriComponentsBuilder uriBuilder) {

        InvoiceResponseDTO created = invoiceService.createInvoice(dto);
        return ResponseEntity
                .created(uriBuilder.path("/api/invoices/{id}")
                        .buildAndExpand(created.getInvoiceId()).toUri())
                .body(created);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceResponseDTO getInvoice(@PathVariable Long invoiceId) {
        return invoiceService.getInvoice(invoiceId);
    }

    @GetMapping
    public Page<InvoiceResponseDTO> listInvoices(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "invoiceId", direction = Sort.Direction.DESC) Pageable pageable) {

        return invoiceService.listInvoices(
                customerId, paymentStatus, archived, fromDate, toDate, pageable);
    }

    @PatchMapping("/{invoiceId}/archive")
    public ResponseEntity<Void> archiveInvoice(@PathVariable Long invoiceId) {
        invoiceService.archiveInvoice(invoiceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{invoiceId}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long invoiceId) {
        invoiceService.deleteInvoice(invoiceId);
        return ResponseEntity.noContent().build();
    }
}