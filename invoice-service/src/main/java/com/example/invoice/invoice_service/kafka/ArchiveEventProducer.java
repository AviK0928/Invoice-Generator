package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArchiveEventProducer {

    private final KafkaTemplate<String, ArchiveEventDTO> archiveKafkaTemplate;
    private static final String TOPIC = "invoice-archived";

    public void publish(ArchiveEventDTO dto) {
        archiveKafkaTemplate.send(TOPIC, String.valueOf(dto.getInvoiceId()), dto);
    }
}
