package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.enums.InvoiceEventType;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.invoice_service.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * No inbox check, deliberately. deleteInvoiceById is unchecked — deleting an
 * invoice that is already gone is a no-op, not an error — so a redelivery
 * produces the same state. An inbox would add a table write per event to
 * prevent nothing. Consumers whose effects are not idempotent
 * (archive-service's archive, export-service's PDF) do check.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceDeletionConsumer {

    private static final String TOPIC = Topics.INVOICE_DELETE;
    private static final String GROUP_ID = "invoice-service-group";
    private static final String CONTAINER_FACTORY = "invoiceDeletionKafkaListenerFactory";

    private final InvoiceService invoiceService;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID, containerFactory = CONTAINER_FACTORY)
    public void consume(InvoiceEventDTO event) {
        if (event.getEventType() != InvoiceEventType.DELETE_INVOICE) {
            log.debug("Skipping non-delete event: {}", event.getEventType());
            return;
        }

        log.info("Received DELETE_INVOICE event for invoiceId={}", event.getInvoiceId());
        invoiceService.deleteInvoiceById(event.getInvoiceId());
    }
}