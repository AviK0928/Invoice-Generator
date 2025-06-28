package com.example.invoice.export_service.mapper;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceItemDTO;
import com.example.invoice.export_service.entity.ExportCustomer;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;

import java.util.List;
import java.util.stream.Collectors;

public class ExportMapper {

    public static ExportCustomer toExportCustomer(InvoiceEventDTO invoiceDTO) {
        return ExportCustomer.builder()
                .customerId(invoiceDTO.getCustomerId())
                .name(invoiceDTO.getName())
                .email(invoiceDTO.getEmail())
                .build();
    }

    public static ExportInvoice toExportInvoice(InvoiceEventDTO invoiceDTO) {
        return ExportInvoice.builder()
                .invoiceId(invoiceDTO.getInvoiceId())
                .customerId(invoiceDTO.getCustomerId())
                .invoiceDate(invoiceDTO.getInvoiceDate())
                .totalAmount(invoiceDTO.getTotalAmount())
                .paymentStatus(invoiceDTO.getPaymentStatus())
                .archived(invoiceDTO.getArchived())
                .createdAt(invoiceDTO.getCreatedAt())
                .contentHash(invoiceDTO.getContentHash())
                .build();
    }

    public static List<ExportInvoiceItem> toExportInvoiceItems(InvoiceEventDTO invoiceDTO, ExportInvoice invoice) {
        List<InvoiceItemDTO> items = invoiceDTO.getItems();
        if (items == null) return List.of();

        return items.stream()
                .map(itemDTO -> ExportInvoiceItem.builder()
                        .description(itemDTO.getDescription())
                        .quantity(itemDTO.getQuantity())
                        .unitPrice(itemDTO.getUnitPrice())
                        .totalPrice(itemDTO.getTotalPrice())
                        .invoice(invoice)
                        .build())
                .collect(Collectors.toList());
    }
}