package com.example.invoice.export_service.dto;

import lombok.*;

import java.time.YearMonth;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyExportResponseDTO {
    private YearMonth month;
    private String fileName;
    private String downloadUrl;
}