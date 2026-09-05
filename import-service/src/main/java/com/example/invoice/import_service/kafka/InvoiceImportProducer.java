package com.example.invoice.import_service.kafka;

import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceImportProducer {

    private final KafkaTemplate<String, InvoiceEventDTO> kafkaTemplate;
    private static final String TOPIC = Topics.INVOICE_IMPORTED;

    public void publish(InvoiceEventDTO event) {
        kafkaTemplate.send(TOPIC, event.getInvoiceId().toString(), event);
    }
}