package com.example.invoice.export_service.entity;

import com.example.invoice.common.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportInvoice {

    @Id
    private Long invoiceId;

    @NotNull
    private Long customerId;

    @NotNull
    private LocalDate invoiceDate;

    @NotNull
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    private Boolean archived;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customerId", insertable = false, updatable = false)
    private ExportCustomer customer;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    private List<ExportInvoiceItem> items;

    private String contentHash;
}