package com.example.invoice.customer_service.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequestDTO {
    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;
}
