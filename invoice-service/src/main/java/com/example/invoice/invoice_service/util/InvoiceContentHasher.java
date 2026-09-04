package com.example.invoice.invoice_service.util;

import com.example.invoice.common.util.HashUtil;
import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Produces a stable fingerprint of an invoice's business content.
 *
 * Deliberately excludes paymentStatus, createdAt and invoiceId: those are
 * mutable state or server-assigned, not content. Two requests describing the
 * same invoice must hash identically no matter what order the items arrive in.
 */
@Component
public class InvoiceContentHasher {

    public String hash(InvoiceRequestDTO dto) {
        String items = dto.getItems().stream()
                .map(this::canonical)
                .sorted()
                .collect(Collectors.joining(";"));

        String canonical = String.join("|",
                String.valueOf(dto.getCustomerId()),
                String.valueOf(dto.getInvoiceDate()),
                items);

        return HashUtil.computeSHA256(canonical);
    }

    private String canonical(InvoiceItemDTO item) {
        return String.join(",",
                item.getDescription().trim().toLowerCase(),
                String.valueOf(item.getQuantity()),
                item.getUnitPrice().stripTrailingZeros().toPlainString());
    }
}