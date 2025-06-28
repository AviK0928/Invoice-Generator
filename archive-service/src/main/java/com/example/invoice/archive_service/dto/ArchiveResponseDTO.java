package com.example.invoice.archive_service.dto;

import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.dto.ArchiveItemDTO;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchiveResponseDTO {
    private Long invoiceId;
    private Long customerId;
    private String name;
    private String email;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private ArchiveEventType eventType;
    private List<ArchiveItemDTO> items;
}