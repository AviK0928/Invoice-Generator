package com.example.invoice.archive_service.mapper;

import com.example.invoice.archive_service.entity.ArchivedInvoice;
import com.example.invoice.archive_service.entity.ArchivedInvoiceItem;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.ArchiveItemDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ArchiveMapper {

    public ArchivedInvoice toEntity(ArchiveEventDTO dto) {
        return ArchivedInvoice.builder()
                .invoiceId(dto.getInvoiceId())
                .customerId(dto.getCustomerId())
                .name(dto.getName())
                .email(dto.getEmail())
                .invoiceDate(dto.getInvoiceDate())
                .paymentStatus(dto.getPaymentStatus().name())
                .totalAmount(dto.getTotalAmount())
                .eventType(dto.getEventType())
                .build();
    }

    public List<ArchivedInvoiceItem> toItemEntities(List<ArchiveItemDTO> dtoList, Long invoiceId) {
        return dtoList.stream()
                .map(item -> ArchivedInvoiceItem.builder()
                        .invoiceId(invoiceId)
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());
    }
    public List<ArchiveItemDTO> toItemDTOs(List<ArchivedInvoiceItem> itemList) {
        return itemList.stream()
                .map(item -> ArchiveItemDTO.builder()
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());
    }
}