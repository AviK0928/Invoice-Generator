package com.example.invoice.import_service.controller;

import com.example.invoice.import_service.dto.ImportSummaryDTO;
import com.example.invoice.import_service.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Imports", description = "Bulk invoice import from CSV")
@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @Operation(summary = "Import invoices from a CSV file", description = """
            Each invoice is imported in its own transaction, so one bad row
            does not discard the rest of the file. The response reports
            per-invoice failures rather than aborting on the first error.
            """)
    @PostMapping(value = "/invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummaryDTO importInvoices(
            @Parameter(description = """
                    CSV with a header row. Columns: invoiceId, customerId, name, email,
                    invoiceDate (yyyy-MM-dd), paymentStatus, totalAmount, description,
                    quantity, unitPrice, totalPrice. One row per line item; invoice-level
                    columns repeat on each row of the same invoiceId.
                    """) @RequestParam("file") MultipartFile file) {
        return importService.importInvoiceData(file);
    }
}