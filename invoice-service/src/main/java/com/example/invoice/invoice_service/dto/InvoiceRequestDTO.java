package com.example.invoice.invoice_service.dto;

import com.example.invoice.common.enums.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequestDTO {

    @NotNull
    private Long customerId;

    @NotNull
    private LocalDate invoiceDate;

    @NotNull
    private PaymentStatus paymentStatus;

    @NotEmpty
    private List<@Valid InvoiceItemDTO> items;
}