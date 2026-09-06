package com.example.invoice.export_service.kafka;

import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.export_service.service.PdfRequestHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfRequestConsumer {

    private final PdfRequestHandlerService handlerService;

    @KafkaListener(topics = Topics.INVOICE_PDF_REQUESTED, groupId = "export-service-group", containerFactory = "pdfRequestKafkaListenerFactory")
    public void consume(
            PdfRequestEventDTO event,
            @Header(name = "X-Event-Id", required = false) String eventId) {
        handlerService.handle(event, eventId);
    }
}