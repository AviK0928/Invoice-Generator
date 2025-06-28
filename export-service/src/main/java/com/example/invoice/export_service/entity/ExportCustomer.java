package com.example.invoice.export_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
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