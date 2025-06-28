package com.example.invoice.import_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSummaryDTO {
    private int totalRows;
    private int successCount;
    private int failureCount;
    private String message;
}