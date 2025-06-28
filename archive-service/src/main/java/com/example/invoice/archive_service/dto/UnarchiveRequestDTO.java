package com.example.invoice.archive_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnarchiveRequestDTO {

    @NotNull
    private Long invoiceId;
}