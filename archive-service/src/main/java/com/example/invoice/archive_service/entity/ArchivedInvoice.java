package com.example.invoice.archive_service.entity;

import com.example.invoice.common.enums.ArchiveEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "archived_invoices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivedInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long archivedInvoiceId;

    @NotNull
    private Long invoiceId;

    @NotBlank
    private String name;

    @NotBlank
    private String email;

    @NotNull
    private Long customerId;

    @NotNull
    private LocalDate invoiceDate;

    @NotNull
    private BigDecimal totalAmount;

    private String paymentStatus;

    @Enumerated(EnumType.STRING)
    private ArchiveEventType eventType;

    @OneToMany(mappedBy = "archivedInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArchivedInvoiceItem> items;
}
