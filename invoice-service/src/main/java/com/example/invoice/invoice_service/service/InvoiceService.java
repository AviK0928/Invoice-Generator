package com.example.invoice.invoice_service.service;

import com.example.invoice.common.enums.InvoiceEventType;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.enums.ArchiveEventType;

import com.example.invoice.common.kafka.dto.ArchiveItemDTO;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.dto.InvoiceResponseDTO;
import com.example.invoice.invoice_service.entity.Invoice;
import com.example.invoice.invoice_service.entity.InvoiceItem;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.kafka.ArchiveEventProducer;
import com.example.invoice.invoice_service.kafka.InvoiceEventProducer;
import com.example.invoice.invoice_service.mapper.InvoiceMapper;
import com.example.invoice.invoice_service.repository.InvoiceItemRepository;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import com.example.invoice.invoice_service.util.InvoiceContentHasher;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

        private final InvoiceRepository invoiceRepository;
        private final InvoiceItemRepository invoiceItemRepository;
        private final LocalCustomerRepository customerRepository;
        private final InvoiceMapper mapper;
        private final InvoiceContentHasher hasher;
        private final InvoiceEventProducer producer;
        private final ArchiveEventProducer archiveEventProducer;

        @Transactional
        public InvoiceResponseDTO createInvoice(InvoiceRequestDTO dto) {
                if (!customerRepository.existsById(dto.getCustomerId())) {
                        throw new IllegalArgumentException("Invalid customerId: " + dto.getCustomerId());
                }

                String contentHash = hasher.hash(dto);

                // Idempotent create: an identical invoice is the same invoice, not a new one.
                Optional<Invoice> existing = invoiceRepository.findByContentHash(contentHash);
                if (existing.isPresent()) {
                        return mapper.toDTO(existing.get());
                }

                BigDecimal totalAmount = dto.getItems().stream()
                                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                Invoice savedInvoice = invoiceRepository.save(mapper.toEntity(dto, totalAmount, contentHash));

                List<InvoiceItem> items = mapper.toItemEntities(dto.getItems(), savedInvoice);
                invoiceItemRepository.saveAll(items);
                savedInvoice.setItems(items);

                producer.publish(InvoiceEventDTO.builder()
                                .invoiceId(savedInvoice.getInvoiceId())
                                .customerId(savedInvoice.getCustomerId())
                                .totalAmount(savedInvoice.getTotalAmount())
                                .paymentStatus(savedInvoice.getPaymentStatus())
                                .eventType(InvoiceEventType.CREATED)
                                .build());

                return mapper.toDTO(savedInvoice);
        }

        @Transactional
        public void archiveInvoice(Long invoiceId) {
                Invoice invoice = invoiceRepository.findById(invoiceId)
                                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
                LocalCustomer customer = customerRepository.findById(invoice.getCustomerId())
                                .orElseThrow(() -> new IllegalStateException("Customer not found"));
                invoice.setArchived(true);
                invoiceRepository.save(invoice);

                InvoiceEventDTO invoiceEvent = InvoiceEventDTO.builder()
                                .invoiceId(invoice.getInvoiceId())
                                .customerId(invoice.getCustomerId())
                                .totalAmount(invoice.getTotalAmount())
                                .paymentStatus(invoice.getPaymentStatus())
                                .eventType(InvoiceEventType.ARCHIVED)
                                .build();

                producer.publish(invoiceEvent);

                ArchiveEventDTO archiveEvent = ArchiveEventDTO.builder()
                                .invoiceId(invoice.getInvoiceId())
                                .customerId(invoice.getCustomerId())
                                .name(customer.getName())
                                .email(customer.getEmail())
                                .invoiceDate(invoice.getInvoiceDate())
                                .paymentStatus(invoice.getPaymentStatus())
                                .totalAmount(invoice.getTotalAmount())
                                .items(invoice.getItems().stream()
                                                .map(item -> new ArchiveItemDTO(
                                                                item.getDescription(),
                                                                item.getQuantity(),
                                                                item.getUnitPrice(),
                                                                item.getTotalPrice()))
                                                .toList())
                                .eventType(ArchiveEventType.MANUAL_ARCHIVE)
                                .build();

                archiveEventProducer.publish(archiveEvent);
        }

        @Transactional
        public void deleteInvoiceById(Long invoiceId) {
                invoiceItemRepository.deleteByInvoice_InvoiceId(invoiceId);
                invoiceRepository.deleteById(invoiceId);
        }
}