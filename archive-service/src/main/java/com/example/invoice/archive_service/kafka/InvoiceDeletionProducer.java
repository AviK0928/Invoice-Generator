package com.example.invoice.archive_service.kafka;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceDeletionProducer {

    private static final String TOPIC = "invoice-delete";

    private final KafkaTemplate<String, InvoiceEventDTO> invoiceKafkaTemplate;

    public void publish(InvoiceEventDTO dto) {
        invoiceKafkaTemplate.send(TOPIC, dto.getInvoiceId().toString(), dto);
    }
}