package com.example.invoice.common.inbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    /** Producer-assigned, from the X-Event-Id header. */
    @Id
    @Column(length = 128)
    private String eventId;

    @Column(length = 64)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime processedAt;
}