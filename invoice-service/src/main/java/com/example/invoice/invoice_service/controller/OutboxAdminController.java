package com.example.invoice.invoice_service.controller;

import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.common.outbox.OutboxDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operational view of the outbox.
 *
 * A backlog is the outbox's silent failure mode: the API keeps working while
 * nothing reaches downstream. Without these, seeing or recovering from that
 * requires a psql session.
 */
@Tag(name = "Outbox (admin)", description = "Event delivery status and recovery")
@RestController
@RequestMapping("/api/invoices/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxEventRepository repository;
    private final OutboxDispatcher dispatcher;

    @Value("${outbox.max-attempts:300}")
    private int maxAttempts;

    @Operation(summary = "Outbox depth", description = "Pending events awaiting delivery, and those abandoned after exhausting retries.")
    @GetMapping("/status")
    public Map<String, Long> status() {
        return Map.of(
                "pending", repository.countByPublishedAtIsNull(),
                "abandoned", repository.countByAttemptsGreaterThanEqualAndPublishedAtIsNull(maxAttempts));
    }

    @Operation(summary = "Requeue abandoned events", description = "Resets the attempt counter so the dispatcher reconsiders them.")
    @PostMapping("/requeue")
    public Map<String, Integer> requeue() {
        return Map.of("requeued", dispatcher.requeueAbandoned());
    }
}