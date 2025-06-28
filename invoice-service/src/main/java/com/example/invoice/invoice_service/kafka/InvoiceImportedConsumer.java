//package com.example.invoice.invoice_service.kafka;
//
//import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
//import com.example.invoice.common.kafka.dto.InvoiceItemDTO as KafkaInvoiceItemDTO;
//import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
//import com.example.invoice.invoice_service.entity.Invoice;
//import com.example.invoice.invoice_service.entity.InvoiceItem;
//import com.example.invoice.invoice_service.mapper.InvoiceMapper;
//import com.example.invoice.invoice_service.repository.InvoiceItemRepository;
//import com.example.invoice.invoice_service.repository.InvoiceRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//
//@Component
//@RequiredArgsConstructor
//public class InvoiceImportedConsumer {
//
//    private final InvoiceRepository invoiceRepository;
//    private final InvoiceItemRepository itemRepository;
//    private final InvoiceMapper invoiceMapper;
//
//    @KafkaListener(topics = "invoice-imported", groupId = "invoice-service-group")
//    public void consume(InvoiceEventDTO event) {
//        // Map and save invoice
//        Invoice invoice = invoiceMapper.toEntity(event);
//        invoiceRepository.save(invoice);
//
//        // Clean up any old items (in case of re-import)
//        itemRepository.deleteAllByInvoiceId(invoice.getInvoiceId());
//
//        // Convert Kafka DTOs -> service DTOs -> entities
//        List<InvoiceItemDTO> serviceDTOs = event.getItems().stream()
//                .map(this::toServiceItemDTO)
//                .collect(Collectors.toList());
//
//        List<InvoiceItem> items = invoiceMapper.toItemEntities(serviceDTOs, invoice);
//        itemRepository.saveAll(items);
//    }
//
//    private InvoiceItemDTO toServiceItemDTO(KafkaInvoiceItemDTO dto) {
//        return InvoiceItemDTO.builder()
//                .description(dto.getDescription())
//                .quantity(dto.getQuantity())
//                .unitPrice(dto.getUnitPrice())
//                .build();
//    }
//}