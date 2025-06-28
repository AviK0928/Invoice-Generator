package com.example.invoice.invoice_service.scheduler;

import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.kafka.dto.ArchiveItemDTO;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.invoice_service.entity.Invoice;
import com.example.invoice.invoice_service.entity.LocalCustomer;
import com.example.invoice.invoice_service.kafka.ArchiveEventProducer;
import com.example.invoice.invoice_service.repository.InvoiceRepository;
import com.example.invoice.invoice_service.repository.LocalCustomerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoArchiveScheduler {

    private final InvoiceRepository invoiceRepository;
    private final LocalCustomerRepository customerRepository;
    private final ArchiveEventProducer archiveEventProducer;

    @Scheduled(cron = "0 0 2 * * *") // runs every day at 2 AM
    @Transactional
    public void autoArchiveInvoices() {
        LocalDate cutoffDate = LocalDate.now().minusMonths(1);

        List<Invoice> invoicesToArchive = invoiceRepository
                .findByArchivedFalseAndInvoiceDateBefore(cutoffDate);

        for (Invoice invoice : invoicesToArchive) {
            LocalCustomer customer = customerRepository.findById(invoice.getCustomerId())
                    .orElse(null);

            if (customer == null) {
                log.warn("Skipping invoice {} due to missing customer", invoice.getInvoiceId());
                continue;
            }

            invoice.setArchived(true);
            invoiceRepository.save(invoice);

            ArchiveEventDTO event = ArchiveEventDTO.builder()
                    .invoiceId(invoice.getInvoiceId())
                    .customerId(invoice.getCustomerId())
                    .name(customer.getName())
                    .email(customer.getEmail())
                    .invoiceDate(invoice.getInvoiceDate())
                    .paymentStatus(invoice.getPaymentStatus())
                    .totalAmount(invoice.getTotalAmount())
                    .items(invoice.getItems().stream().map(item ->
                            new ArchiveItemDTO(
                                    item.getDescription(),
                                    item.getQuantity(),
                                    item.getUnitPrice(),
                                    item.getTotalPrice())
                    ).toList())
                    .eventType(ArchiveEventType.AUTO_ARCHIVE)
                    .build();

            archiveEventProducer.publish(event);
        }
    }
}
