package com.example.invoice.common.kafka.dto;

import com.example.invoice.common.enums.EventType;
import com.example.invoice.common.enums.InvoiceEventType;
import com.example.invoice.common.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceEventDTO {
    private Long invoiceId;
    private Long customerId;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private InvoiceEventType eventType;

    // New fields
    private LocalDate invoiceDate;
    private Boolean archived;
    private LocalDateTime createdAt;
    private String contentHash;

    private String name;
    private String email;

    private List<InvoiceItemDTO> items;
}
