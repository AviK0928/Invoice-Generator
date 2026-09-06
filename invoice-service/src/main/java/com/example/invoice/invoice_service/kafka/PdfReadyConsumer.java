package com.example.invoice.invoice_service.kafka;

import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.PdfRequestEventDTO;
import com.example.invoice.invoice_service.service.PdfReadyHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfReadyConsumer {

    private final PdfReadyHandlerService handlerService;

    @KafkaListener(topics = Topics.INVOICE_PDF_READY, groupId = "invoice-service-group", containerFactory = "pdfReadyKafkaListenerFactory")
    public void consume(
            PdfRequestEventDTO event,
            @Header(name = "X-Event-Id", required = false) String eventId) {
        handlerService.handle(event, eventId);
    }
}