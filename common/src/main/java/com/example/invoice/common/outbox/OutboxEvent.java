package com.example.invoice.common.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Domain type this event concerns, e.g. "Invoice". */
    @Column(nullable = false, length = 64)
    private String aggregateType;

    @Column(nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, length = 128)
    private String topic;

    /** Kafka partition key — keeps events for one aggregate ordered. */
    @Column(nullable = false, length = 128)
    private String eventKey;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Null until delivered. The dispatcher's queue is defined by this. */
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(columnDefinition = "text")
    private String lastError;

    /** Null means due now. Set on failure to defer the next attempt. */
    private LocalDateTime nextAttemptAt;
}