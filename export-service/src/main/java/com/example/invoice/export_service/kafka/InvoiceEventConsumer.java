package com.example.invoice.export_service.kafka;

import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.export_service.service.InvoiceEventHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceEventConsumer {

    private final InvoiceEventHandlerService handlerService;

    /**
     * X-Event-Id is optional: publishers not yet migrated to the outbox send
     * no header, and those events are processed without a dedup check.
     */
    @KafkaListener(topics = Topics.INVOICE_EVENTS, groupId = "export-service-group", containerFactory = "invoiceEventKafkaListenerFactory")
    public void consumeInvoiceEvent(
            InvoiceEventDTO event,
            @Header(name = "X-Event-Id", required = false) String eventId,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        handlerService.processInvoiceEvent(event, eventId);
    }
}