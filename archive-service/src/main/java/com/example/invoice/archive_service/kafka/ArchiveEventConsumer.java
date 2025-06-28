package com.example.invoice.archive_service.kafka;

import com.example.invoice.archive_service.service.ArchiveService;
import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArchiveEventConsumer {

    private final ArchiveService archiveService;

    private static final String TOPIC = "invoice-archived";
    private static final String GROUP_ID = "archive-service-group";
    private static final String CONTAINER_FACTORY = "archiveKafkaListenerContainerFactory";

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP_ID,
            containerFactory = CONTAINER_FACTORY
    )

    public void consume(ArchiveEventDTO dto) {
        if (dto.getEventType() != ArchiveEventType.MANUAL_ARCHIVE &&
                dto.getEventType() != ArchiveEventType.AUTO_ARCHIVE) {
            log.debug("Skipping non-archive event: {}", dto.getEventType());
            return;
        }

        archiveService.saveArchivedInvoice(dto);
        log.info("Archived invoice processed for invoiceId={}", dto.getInvoiceId());
    }
}