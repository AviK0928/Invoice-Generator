package com.example.invoice.export_service.service;

import com.example.invoice.common.kafka.dto.InvoiceEventDTO;
import com.example.invoice.export_service.entity.ExportCustomer;
import com.example.invoice.export_service.entity.ExportInvoice;
import com.example.invoice.export_service.entity.ExportInvoiceItem;
import com.example.invoice.export_service.mapper.ExportMapper;
import com.example.invoice.export_service.repository.ExportCustomerRepository;
import com.example.invoice.export_service.repository.ExportInvoiceItemRepository;
import com.example.invoice.export_service.repository.ExportInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceEventHandlerService {

    private final ExportCustomerRepository customerRepository;
    private final ExportInvoiceRepository invoiceRepository;
    private final ExportInvoiceItemRepository invoiceItemRepository;
    private final PdfExportService pdfExportService;

    public void processInvoiceEvent(InvoiceEventDTO event) throws Exception {
        ExportCustomer customer = ExportMapper.toExportCustomer(event);
        customerRepository.save(customer);

        ExportInvoice invoice = ExportMapper.toExportInvoice(event);
        invoiceRepository.save(invoice);

        List<ExportInvoiceItem> oldItems = invoiceItemRepository.findAllByInvoice(invoice);
        invoiceItemRepository.deleteAll(oldItems);

        List<ExportInvoiceItem> newItems = ExportMapper.toExportInvoiceItems(event, invoice);
        invoiceItemRepository.saveAll(newItems);

        File tempDir = Files.createTempDirectory("invoice_pdfs").toFile();
        pdfExportService.generatePdfForInvoice(invoice, tempDir);
    }
}