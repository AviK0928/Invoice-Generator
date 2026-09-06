package com.example.invoice.export_service.controller;

import com.example.invoice.export_service.entity.PdfDocument;
import com.example.invoice.export_service.exception.PdfDocumentNotFoundException;
import com.example.invoice.export_service.repository.PdfDocumentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/exports/pdf")
@RequiredArgsConstructor
@Tag(name = "Invoice PDFs", description = "Download an asynchronously generated PDF")
public class PdfDownloadController {

    private final PdfDocumentRepository pdfDocumentRepository;

    /**
     * One-shot download: the row is deleted as the bytes are returned.
     *
     * Deleted before the response is written rather than after. The alternative
     * — delete on success — survives a dropped connection but needs the delete
     * to happen outside the transaction that read the bytes, and a crash
     * between the two leaves a row nothing will ever remove. A user who loses
     * the download re-requests; a leaked row is there forever.
     */
    @Operation(summary = "Download a generated PDF", description = "Returns the PDF once. It is deleted on download, so a "
            + "second call is a 404 and the client must request a new one.")
    @GetMapping("/{requestId}")
    @Transactional
    public ResponseEntity<byte[]> download(@PathVariable UUID requestId) {
        PdfDocument document = pdfDocumentRepository.findById(requestId)
                .orElseThrow(() -> new PdfDocumentNotFoundException(requestId));

        byte[] content = document.getContent();
        pdfDocumentRepository.delete(document);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice_" + document.getInvoiceId() + ".pdf\"")
                .body(content);
    }
}