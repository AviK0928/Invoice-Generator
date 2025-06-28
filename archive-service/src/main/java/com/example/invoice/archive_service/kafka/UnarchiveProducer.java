package com.example.invoice.archive_service.kafka;

import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnarchiveProducer {

    private static final String TOPIC = "unarchive-invoices";

    private final KafkaTemplate<String, ArchiveEventDTO> archiveKafkaTemplate;

    public void publish(ArchiveEventDTO dto) {
        archiveKafkaTemplate.send(TOPIC, dto.getInvoiceId().toString(), dto);
    }
}