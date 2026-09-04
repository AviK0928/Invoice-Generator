package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.enums.InvoiceEventType;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.invoice_service.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceDeletionConsumer {

    private static final String TOPIC = "invoice-delete";
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