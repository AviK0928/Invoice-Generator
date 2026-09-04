package com.example.invoice.export_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "export_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportCustomer {

    @Id
    private Long customerId;

    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;
}