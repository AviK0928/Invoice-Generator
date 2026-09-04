package com.example.invoice.invoice_service.mapper;

import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.dto.InvoiceResponseDTO;
import com.example.invoice.invoice_service.entity.Invoice;
import com.example.invoice.invoice_service.entity.InvoiceItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class InvoiceMapper {

    public Invoice toEntity(InvoiceRequestDTO dto, BigDecimal totalAmount, String contentHash) {
        return Invoice.builder()
                .customerId(dto.getCustomerId())
                .invoiceDate(dto.getInvoiceDate())
                .paymentStatus(dto.getPaymentStatus())
                .archived(false)
                .createdAt(LocalDateTime.now())
                .totalAmount(totalAmount)
                .contentHash(contentHash)
                .build();
    }

    public InvoiceResponseDTO toDTO(Invoice invoice) {
        return InvoiceResponseDTO.builder()
                .invoiceId(invoice.getInvoiceId())
                .customerId(invoice.getCustomerId())
                .invoiceDate(invoice.getInvoiceDate())
                .totalAmount(invoice.getTotalAmount())
                .paymentStatus(invoice.getPaymentStatus())
                .archived(invoice.getArchived())
                .items(toItemDTOList(invoice.getItems()))
                .build();
    }

    public List<InvoiceItem> toItemEntities(List<InvoiceItemDTO> dtos, Invoice invoice) {
        return dtos.stream().map(dto -> InvoiceItem.builder()
                .invoice(invoice)
                .description(dto.getDescription())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .totalPrice(dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity())))
                .build()).collect(Collectors.toList());
    }

    public List<InvoiceItemDTO> toItemDTOList(List<InvoiceItem> items) {
        return items.stream().map(item -> InvoiceItemDTO.builder()
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .build()).collect(Collectors.toList());
    }
}