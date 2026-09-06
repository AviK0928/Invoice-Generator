package com.example.invoice.invoice_service.controller;

import com.example.invoice.invoice_service.dto.PdfRequestResponseDTO;
import com.example.invoice.invoice_service.entity.PdfRequest;
import com.example.invoice.invoice_service.enums.PdfRequestStatus;
import com.example.invoice.invoice_service.service.PdfRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice PDFs", description = "Asynchronous PDF generation")
public class PdfRequestController {

    /**
     * Where export-service serves the finished PDF. A path, not a full URL:
     * the gateway owns the host, and the client is already talking to it.
     */
    private static final String DOWNLOAD_PATH = "/api/exports/pdf/";

    private final PdfRequestService pdfRequestService;

    @Operation(summary = "Request a PDF for an invoice", description = "Returns 202 immediately. export-service renders the "
            + "PDF asynchronously; poll the status endpoint until READY.")
    @PostMapping("/{invoiceId}/pdf")
    public ResponseEntity<PdfRequestResponseDTO> request(@PathVariable Long invoiceId) {
        PdfRequest request = pdfRequestService.request(invoiceId);

        // 202, not 201: nothing has been generated yet. Location points at the
        // status resource, which is the only thing that exists at this point.
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/invoices/pdf-requests/" + request.getRequestId()))
                .body(toDto(request));
    }

    @Operation(summary = "Check a PDF request", description = "PENDING until export-service reports back, then READY "
            + "with a download URL. The PDF is deleted once downloaded.")
    @GetMapping("/pdf-requests/{requestId}")
    public ResponseEntity<PdfRequestResponseDTO> status(@PathVariable UUID requestId) {
        return ResponseEntity.ok(toDto(pdfRequestService.status(requestId)));
    }

    private PdfRequestResponseDTO toDto(PdfRequest request) {
        return PdfRequestResponseDTO.builder()
                .requestId(request.getRequestId())
                .invoiceId(request.getInvoiceId())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .completedAt(request.getCompletedAt())
                .downloadUrl(request.getStatus() == PdfRequestStatus.READY
                        ? DOWNLOAD_PATH + request.getRequestId()
                        : null)
                .build();
    }
}