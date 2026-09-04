package com.example.invoice.import_service.dto;

import java.util.List;

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
    private List<String> errors;
}