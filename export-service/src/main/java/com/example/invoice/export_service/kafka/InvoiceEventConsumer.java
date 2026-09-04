package com.example.invoice.export_service.kafka;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.export_service.service.InvoiceEventHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceEventConsumer {

    private final InvoiceEventHandlerService handlerService;

    @KafkaListener(topics = "invoice-events", groupId = "export-service-group", containerFactory = "invoiceEventKafkaListenerFactory")
    public void consumeInvoiceEvent(InvoiceEventDTO event) throws Exception {
        handlerService.processInvoiceEvent(event);
    }
}