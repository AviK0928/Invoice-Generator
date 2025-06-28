package com.example.invoice.import_service.mapper;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.import_service.entity.ImportInvoice;
import com.example.invoice.import_service.entity.ImportInvoiceItem;
import org.apache.commons.csv.CSVRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ImportMapper {

    public static ImportInvoice toInvoice(List<CSVRecord> records, List<ImportInvoiceItem> items, String contentHash) {
        CSVRecord first = records.get(0);

        return ImportInvoice.builder()
                .invoiceId(Long.parseLong(first.get("invoiceId")))
                .customerId(Long.parseLong(first.get("customerId")))
                .name(first.get("name"))
                .email(first.get("email"))
                .invoiceDate(LocalDate.parse(first.get("invoiceDate")))
                .paymentStatus(PaymentStatus.valueOf(first.get("paymentStatus")))
                .totalAmount(new BigDecimal(first.get("totalAmount")))
                .contentHash(contentHash)
                .items(items)
                .build();
    }

    public static ImportInvoiceItem toInvoiceItem(CSVRecord record, ImportInvoice invoice) {
        return ImportInvoiceItem.builder()
                .invoice(invoice)
                .description(record.get("description"))
                .quantity(Integer.parseInt(record.get("quantity")))
                .unitPrice(new BigDecimal(record.get("unitPrice")))
                .totalPrice(new BigDecimal(record.get("totalPrice")))
                .build();
    }
}
