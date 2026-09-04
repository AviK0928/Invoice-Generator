package com.example.invoice.export_service.controller;

import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.exception.ExportInvoiceNotFoundException;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import com.example.invoice.export_service.service.ExportService;
import com.example.invoice.export_service.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final PdfExportService pdfExportService;
    private final ExportInvoiceRepository exportInvoiceRepository;

    @GetMapping("/monthly-zip")
    public ResponseEntity<byte[]> downloadMonthlyZip(
            @RequestParam("month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) throws Exception {

        byte[] zip = exportService.generateMonthlyZip(month);
        return ResponseEntity.ok()
                .headers(attachment("invoice_export_" + month + ".zip"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zip.length)
                .body(zip);
    }

    @GetMapping("/invoice/{invoiceId}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long invoiceId) throws Exception {
        ExportInvoice invoice = exportInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ExportInvoiceNotFoundException(invoiceId));

        byte[] pdf = pdfExportService.generatePdfForInvoice(invoice);
        return ResponseEntity.ok()
                .headers(attachment("invoice_" + invoiceId + ".pdf"))
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    private HttpHeaders attachment(String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return headers;
    }
}