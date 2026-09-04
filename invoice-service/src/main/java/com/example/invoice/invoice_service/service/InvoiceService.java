package com.example.invoice.invoice_service.service;

import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.enums.InvoiceEventType;
import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.ArchiveItemDTO;
import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.common.kafka.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import com.example.invoice.invoice_service.dto.InvoiceResponseDTO;
import com.example.invoice.invoice_service.entity.Invoice;
import com.example.invoice.invoice_service.entity.InvoiceItem;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.exception.InvalidCustomerException;
import com.example.invoice.invoice_service.exception.InvoiceNotFoundException;
import com.example.invoice.invoice_service.mapper.InvoiceMapper;
import com.example.invoice.invoice_service.repository.InvoiceItemRepository;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import com.example.invoice.invoice_service.util.InvoiceContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

        private static final String AGGREGATE = "Invoice";
        private static final String TOPIC_INVOICE_EVENTS = "invoice-events";
        private static final String TOPIC_INVOICE_ARCHIVED = "invoice-archived";

        private final InvoiceRepository invoiceRepository;
        private final InvoiceItemRepository invoiceItemRepository;
        private final LocalCustomerRepository customerRepository;
        private final InvoiceMapper mapper;
        private final InvoiceContentHasher hasher;
        private final OutboxWriter outbox;

        // ---------------------------------------------------------------- queries

        @Transactional(readOnly = true)
        public InvoiceResponseDTO getInvoice(Long invoiceId) {
                return invoiceRepository.findWithItemsByInvoiceId(invoiceId)
                                .map(mapper::toDTO)
                                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        }

        @Transactional(readOnly = true)
        public Page<InvoiceResponseDTO> listInvoices(Long customerId,
                        PaymentStatus paymentStatus,
                        Boolean archived,
                        LocalDate fromDate,
                        LocalDate toDate,
                        Pageable pageable) {
                return invoiceRepository
                                .search(customerId, paymentStatus, archived, fromDate, toDate, pageable)
                                .map(mapper::toDTO);
        }

        // --------------------------------------------------------------- commands

        /**
         * Idempotent: two requests with identical content resolve to the same
         * invoice rather than creating a duplicate.
         *
         * Note the existing-invoice path records no event. "Already exists" and
         * "downstream has it" are different questions, and this conflates them —
         * a consumer that missed the original cannot recover it by retrying the
         * create. See docs/adr/004.
         */
        @Transactional
        public InvoiceResponseDTO createInvoice(InvoiceRequestDTO dto) {
                LocalCustomer customer = customerRepository.findById(dto.getCustomerId())
                                .orElseThrow(() -> new InvalidCustomerException(dto.getCustomerId()));

                String contentHash = hasher.hash(dto);

                return invoiceRepository.findByContentHash(contentHash)
                                .map(mapper::toDTO)
                                .orElseGet(() -> persistNew(dto, customer, contentHash));
        }

        @Transactional
        public void archiveInvoice(Long invoiceId) {
                archiveInvoice(invoiceId, ArchiveEventType.MANUAL_ARCHIVE);
        }

        /**
         * Shared by the API and the auto-archive scheduler, which differ only in
         * the event type. Keeping one path means the two cannot drift — the
         * scheduler previously omitted the invoice-events ARCHIVED publish
         * entirely, so export-service never learned about auto-archived invoices.
         */
        @Transactional
        public void archiveInvoice(Long invoiceId, ArchiveEventType archiveType) {
                Invoice invoice = invoiceRepository.findWithItemsByInvoiceId(invoiceId)
                                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

                if (Boolean.TRUE.equals(invoice.getArchived())) {
                        return;
                }

                LocalCustomer customer = customerRepository.findById(invoice.getCustomerId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "Customer " + invoice.getCustomerId() + " missing for invoice "
                                                                + invoiceId));

                invoice.setArchived(true);
                invoiceRepository.save(invoice);

                outbox.record(AGGREGATE, invoiceId, TOPIC_INVOICE_EVENTS,
                                InvoiceEventType.ARCHIVED.name(),
                                toEvent(invoice, customer, InvoiceEventType.ARCHIVED));

                outbox.record(AGGREGATE, invoiceId, TOPIC_INVOICE_ARCHIVED,
                                archiveType.name(),
                                toArchiveEvent(invoice, customer, archiveType));
        }

        @Transactional
        public void deleteInvoice(Long invoiceId) {
                if (!invoiceRepository.existsById(invoiceId)) {
                        throw new InvoiceNotFoundException(invoiceId);
                }
                deleteInvoiceById(invoiceId);
        }

        /**
         * Unchecked delete used by the Kafka delete-event consumer, where the
         * invoice may already be gone and that is not an error.
         */
        @Transactional
        public void deleteInvoiceById(Long invoiceId) {
                invoiceItemRepository.deleteByInvoice_InvoiceId(invoiceId);
                invoiceRepository.deleteById(invoiceId);
        }

        // -------------------------------------------------------------- internals

        private InvoiceResponseDTO persistNew(InvoiceRequestDTO dto, LocalCustomer customer, String contentHash) {
                BigDecimal totalAmount = dto.getItems().stream()
                                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                Invoice invoice = invoiceRepository.save(mapper.toEntity(dto, totalAmount, contentHash));

                List<InvoiceItem> items = mapper.toItemEntities(dto.getItems(), invoice);
                invoiceItemRepository.saveAll(items);
                invoice.setItems(items);

                // The invoice and its event commit together. A broker outage now delays
                // delivery instead of failing the write — this call previously threw
                // "Send failed" and rolled the whole creation back.
                outbox.record(AGGREGATE, invoice.getInvoiceId(), TOPIC_INVOICE_EVENTS,
                                InvoiceEventType.CREATED.name(),
                                toEvent(invoice, customer, InvoiceEventType.CREATED));

                return mapper.toDTO(invoice);
        }

        private InvoiceEventDTO toEvent(Invoice invoice, LocalCustomer customer, InvoiceEventType type) {
                return InvoiceEventDTO.builder()
                                .invoiceId(invoice.getInvoiceId())
                                .customerId(invoice.getCustomerId())
                                .name(customer.getName())
                                .email(customer.getEmail())
                                .invoiceDate(invoice.getInvoiceDate())
                                .totalAmount(invoice.getTotalAmount())
                                .paymentStatus(invoice.getPaymentStatus())
                                .archived(invoice.getArchived())
                                .createdAt(invoice.getCreatedAt())
                                .contentHash(invoice.getContentHash())
                                .eventType(type)
                                .items(invoice.getItems().stream()
                                                .map(item -> InvoiceItemDTO.builder()
                                                                .description(item.getDescription())
                                                                .quantity(item.getQuantity())
                                                                .unitPrice(item.getUnitPrice())
                                                                .totalPrice(item.getTotalPrice())
                                                                .build())
                                                .toList())
                                .build();
        }

        private ArchiveEventDTO toArchiveEvent(Invoice invoice, LocalCustomer customer,
                        ArchiveEventType archiveType) {
                return ArchiveEventDTO.builder()
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
        }
}