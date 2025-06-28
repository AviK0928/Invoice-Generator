package com.example.invoice.invoice_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "local_customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalCustomer {

    @Id
    private Long customerId;

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;
}