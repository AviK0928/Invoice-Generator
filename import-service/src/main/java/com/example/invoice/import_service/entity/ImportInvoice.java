package com.example.invoice.import_service.entity;

import com.example.invoice.common.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportInvoice {

    @Id
    private Long invoiceId;

    @NotNull
    private Long customerId;

    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    @NotNull
    private LocalDate invoiceDate;

    @Enumerated(EnumType.STRING)
    @NotNull
    private PaymentStatus paymentStatus;

    @NotNull
    private BigDecimal totalAmount;

    @NotBlank
    private String contentHash;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImportInvoiceItem> items;
}