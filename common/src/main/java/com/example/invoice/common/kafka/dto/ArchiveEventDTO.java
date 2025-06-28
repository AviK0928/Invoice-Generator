package com.example.invoice.common.kafka.dto;

import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveEventDTO {
    private Long invoiceId;
    private Long customerId;
    private String name;
    private String email;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private List<ArchiveItemDTO> items;
    private ArchiveEventType eventType;
}