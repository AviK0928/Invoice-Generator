package com.example.invoice.export_service.service;

import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;

import java.awt.*;
import java.util.List;
import java.math.BigDecimal;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final ExportInvoiceRepository invoiceRepository;
    private final ExportInvoiceItemRepository itemRepository;

    public byte[] generatePdfForInvoice(ExportInvoice invoice) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        try {
            // Title
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("Invoice #" + invoice.getInvoiceId(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph("Customer: " + invoice.getCustomer().getName()));
            document.add(new Paragraph("Email: " + invoice.getCustomer().getEmail()));
            document.add(new Paragraph("Invoice Date: " + invoice.getInvoiceDate()));
            document.add(new Paragraph("Payment Status: " + invoice.getPaymentStatus()));

            document.add(new Paragraph(" ")); // blank line

            // Table for items
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 3, 1, 2, 2, 2 });

            addTableHeader(table);
            addRows(table, invoice.getItems());

            document.add(table);

            // Total
            BigDecimal total = invoice.getItems().stream()
                    .map(ExportInvoiceItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Paragraph totalPara = new Paragraph("Total Amount: " + total, new Font(Font.HELVETICA, 14, Font.BOLD));
            totalPara.setAlignment(Element.ALIGN_RIGHT);
            document.add(totalPara);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private void addTableHeader(PdfPTable table) {
        Stream.of("Description", "Qty", "Unit Price", "Total Price", "Remarks")
                .forEach(columnTitle -> {
                    PdfPCell header = new PdfPCell();
                    header.setBackgroundColor(Color.LIGHT_GRAY);
                    header.setBorderWidth(1);
                    header.setPhrase(new Phrase(columnTitle));
                    table.addCell(header);
                });
    }

    private void addRows(PdfPTable table, List<ExportInvoiceItem> items) {
        for (ExportInvoiceItem item : items) {
            table.addCell(item.getDescription());
            table.addCell(String.valueOf(item.getQuantity()));
            table.addCell(item.getUnitPrice().toString());
            table.addCell(item.getTotalPrice().toString());
            table.addCell(""); // empty remarks cell
        }
    }
}