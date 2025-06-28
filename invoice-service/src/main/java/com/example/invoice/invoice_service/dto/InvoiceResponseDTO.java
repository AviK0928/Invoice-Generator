package com.example.invoice.invoice_service.dto;

import com.example.invoice.common.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponseDTO {

    private Long invoiceId;
    private Long customerId;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private boolean archived;
    private List<InvoiceItemDTO> items;
}