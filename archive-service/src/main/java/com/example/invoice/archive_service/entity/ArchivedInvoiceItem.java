package com.example.invoice.archive_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "archived_invoice_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivedInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long archivedInvoiceItemId;

    @NotBlank
    private String description;

    @NotNull
    private Integer quantity;

    @NotNull
    private BigDecimal unitPrice;

    @NotNull
    private BigDecimal totalPrice;

    @NotNull
    private Long invoiceId;

    @ManyToOne
    @JoinColumn(name = "archived_invoice_id") // or the correct FK column name
    private ArchivedInvoice archivedInvoice;
}