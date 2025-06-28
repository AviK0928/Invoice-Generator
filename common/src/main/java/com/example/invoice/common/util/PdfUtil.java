package com.example.invoice.common.util;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;

public class PdfUtil {

    public static byte[] generateInvoicePdf(InvoiceEventDTO invoice) throws Exception {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 12);

        document.add(new Paragraph("Invoice", titleFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        addRow(table, "Invoice ID", invoice.getInvoiceId().toString(), labelFont, valueFont);
        addRow(table, "Customer ID", invoice.getCustomerId().toString(), labelFont, valueFont);
        addRow(table, "Total Amount", invoice.getTotalAmount().toPlainString(), labelFont, valueFont);
        addRow(table, "Payment Status", invoice.getPaymentStatus().name(), labelFont, valueFont);
        addRow(table, "Event Type", invoice.getEventType().name(), labelFont, valueFont);

        document.add(table);
        document.close();

        return out.toByteArray();
    }

    private static void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}