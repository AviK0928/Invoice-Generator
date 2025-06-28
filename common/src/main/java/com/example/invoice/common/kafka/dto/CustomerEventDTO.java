package com.example.invoice.common.kafka.dto;

import com.example.invoice.common.enums.EventType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEventDTO {
    private Long customerId;
    private String name;
    private String email;
    private EventType eventType;
}