package com.example.invoice.app.outbox;

import com.example.invoice.archive_service.kafka.ArchiveEventConsumer;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.CustomerEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.common.outbox.OutboxEvent;
import com.example.invoice.common.outbox.OutboxEventPublisher;
import com.example.invoice.export_service.kafka.InvoiceEventConsumer;
import com.example.invoice.export_service.kafka.PdfRequestConsumer;
import com.example.invoice.invoice_service.kafka.CustomerEventConsumer;
import com.example.invoice.invoice_service.kafka.InvoiceDeletionConsumer;
import com.example.invoice.invoice_service.kafka.PdfReadyConsumer;
import com.example.invoice.invoice_service.kafka.UnarchiveConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers outbox events by calling the consumers directly. Selected by
 * outbox.transport=in-process; the Kafka publisher is absent under that
 * setting, so there is no ambiguity about which one the dispatcher gets.
 *
 * The consumers are the real ones, not reimplementations. Excluding Kafka
 * auto-configuration leaves @KafkaListener inert, so these are ordinary beans
 * whose methods can be invoked — which keeps the event-type filters in
 * ArchiveEventConsumer, InvoiceDeletionConsumer and UnarchiveConsumer in one
 * place rather than duplicated into a routing table that would drift.
 *
 * REQUIRES_NEW is load-bearing. The dispatcher's dispatch() is @Transactional
 * and catches per event so one bad message cannot stall a batch. Run a handler
 * inline and its @Transactional joins that transaction; a handler failure marks
 * it rollback-only, the catch swallows the exception, and then saveAll dies with
 * UnexpectedRollbackException — losing the whole batch to one event. A separate
 * transaction rolls back alone and the exception reaches the dispatcher, which
 * records the failure and backs off exactly as it does for a broker error.
 *
 * Delivery stays at-least-once. The handler commits before the row is marked
 * published, so a crash in between redelivers — which is the same window Kafka
 * has, and why the consumers key idempotency on the event id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InProcessOutboxEventPublisher implements OutboxEventPublisher {

    private final ObjectMapper objectMapper;

    private final CustomerEventConsumer customerEventConsumer;
    private final InvoiceDeletionConsumer invoiceDeletionConsumer;
    private final UnarchiveConsumer unarchiveConsumer;
    private final PdfReadyConsumer pdfReadyConsumer;
    private final InvoiceEventConsumer invoiceEventConsumer;
    private final PdfRequestConsumer pdfRequestConsumer;
    private final ArchiveEventConsumer archiveEventConsumer;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String eventId, OutboxEvent event) throws Exception {
        String topic = event.getTopic();

        switch (topic) {
            case Topics.CUSTOMER_EVENTS ->
                customerEventConsumer.consume(read(event, CustomerEventDTO.class));

            case Topics.INVOICE_EVENTS ->
                invoiceEventConsumer.consumeInvoiceEvent(
                        read(event, InvoiceEventDTO.class), eventId, topic);

            case Topics.INVOICE_ARCHIVED ->
                archiveEventConsumer.consume(read(event, ArchiveEventDTO.class), eventId);

            case Topics.INVOICE_DELETE ->
                invoiceDeletionConsumer.consume(read(event, InvoiceEventDTO.class));

            case Topics.UNARCHIVE_INVOICES ->
                unarchiveConsumer.consume(read(event, ArchiveEventDTO.class));

            case Topics.INVOICE_PDF_REQUESTED ->
                pdfRequestConsumer.consume(read(event, PdfRequestEventDTO.class), eventId);

            case Topics.INVOICE_PDF_READY ->
                pdfReadyConsumer.consume(read(event, PdfRequestEventDTO.class), eventId);

            // Published by import-service and consumed by nobody — the one
            // consumer was deleted in Phase 0. Dropped here rather than left to
            // the default below, which would retry it forever.
            case Topics.INVOICE_IMPORTED ->
                log.debug("Discarding {} event {}: no consumer", topic, eventId);

            // A topic with no route is a programming error, not a transient
            // failure. Throwing puts it through the dispatcher's backoff and
            // leaves the row visible in the outbox rather than dropping it
            // silently.
            default -> throw new IllegalStateException(
                    "No in-process consumer for topic " + topic + " (event " + eventId + ")");
        }
    }

    private <T> T read(OutboxEvent event, Class<T> type) throws Exception {
        return objectMapper.readValue(event.getPayload(), type);
    }
}