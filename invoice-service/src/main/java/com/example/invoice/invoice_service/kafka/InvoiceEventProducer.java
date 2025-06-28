package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceEventProducer {

    private final KafkaTemplate<String, InvoiceEventDTO> kafkaTemplate;
    private final String topic = "invoice-events";

    public void publish(InvoiceEventDTO event) {
        kafkaTemplate.send(topic, event.getInvoiceId().toString(), event);
    }
}