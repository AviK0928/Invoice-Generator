package com.example.invoice.export_service.service;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.mapper.ExportMapper;
import com.example.invoice.export_service.repository.ExportCustomerRepository;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceEventHandlerService {

    private final ExportCustomerRepository customerRepository;
    private final ExportInvoiceRepository invoiceRepository;
    private final ExportInvoiceItemRepository invoiceItemRepository;

    /**
     * Projects an invoice event into export-service's read model.
     *
     * PDFs are generated on demand by ExportController, not here. This method
     * used to create a temp directory and write a PDF into it that nothing ever
     * read or deleted — one orphaned file per event.
     */
    @Transactional
    public void processInvoiceEvent(InvoiceEventDTO event) {
        customerRepository.save(ExportMapper.toExportCustomer(event));

        ExportInvoice invoice = invoiceRepository.save(ExportMapper.toExportInvoice(event));

        invoiceItemRepository.deleteAll(invoiceItemRepository.findAllByInvoice(invoice));
        invoiceItemRepository.saveAll(ExportMapper.toExportInvoiceItems(event, invoice));
    }
}