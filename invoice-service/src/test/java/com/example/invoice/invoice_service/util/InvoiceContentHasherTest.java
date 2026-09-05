package com.example.invoice.invoice_service.util;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.invoice_service.dto.InvoiceItemDTO;
import com.example.invoice.invoice_service.dto.InvoiceRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceContentHasherTest {

    private final InvoiceContentHasher hasher = new InvoiceContentHasher();

    @Test
    @DisplayName("identical content hashes identically")
    void identicalContentHashesIdentically() {
        assertThat(hasher.hash(invoice(item("Widget", 2, "10.00"))))
                .isEqualTo(hasher.hash(invoice(item("Widget", 2, "10.00"))));
    }

    @Test
    @DisplayName("item order does not affect the hash")
    void itemOrderDoesNotAffectTheHash() {
        String a = hasher.hash(invoice(item("Widget", 1, "5.00"), item("Gadget", 2, "3.00")));
        String b = hasher.hash(invoice(item("Gadget", 2, "3.00"), item("Widget", 1, "5.00")));

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("BigDecimal scale does not affect the hash")
    void bigDecimalScaleDoesNotAffectTheHash() {
        // 10.00 and 10.0 are the same amount. Without stripTrailingZeros they
        // are different strings and would hash differently, so a client sending
        // an unscaled price would create a duplicate invoice.
        assertThat(hasher.hash(invoice(item("Widget", 1, "10.00"))))
                .isEqualTo(hasher.hash(invoice(item("Widget", 1, "10.0"))));
    }

    @Test
    @DisplayName("payment status is excluded — it is mutable state, not content")
    void paymentStatusIsExcluded() {
        InvoiceRequestDTO pending = invoice(item("Widget", 1, "5.00"));
        pending.setPaymentStatus(PaymentStatus.PENDING);

        InvoiceRequestDTO paid = invoice(item("Widget", 1, "5.00"));
        paid.setPaymentStatus(PaymentStatus.SUCCESSFUL);

        assertThat(hasher.hash(pending)).isEqualTo(hasher.hash(paid));
    }

    @Test
    @DisplayName("different content hashes differently")
    void differentContentHashesDifferently() {
        assertThat(hasher.hash(invoice(item("Widget", 2, "10.00"))))
                .isNotEqualTo(hasher.hash(invoice(item("Widget", 3, "10.00"))));
    }

    @Test
    @DisplayName("description is case- and whitespace-insensitive")
    void descriptionIsNormalised() {
        assertThat(hasher.hash(invoice(item("  Widget  ", 1, "5.00"))))
                .isEqualTo(hasher.hash(invoice(item("widget", 1, "5.00"))));
    }

    @Test
    @DisplayName("hash is SHA-256 hex")
    void hashIsSha256Hex() {
        assertThat(hasher.hash(invoice(item("Widget", 1, "5.00"))))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    // ------------------------------------------------------------- fixtures

    private InvoiceRequestDTO invoice(InvoiceItemDTO... items) {
        InvoiceRequestDTO dto = new InvoiceRequestDTO();
        dto.setCustomerId(1L);
        dto.setInvoiceDate(LocalDate.of(2026, 9, 5));
        dto.setPaymentStatus(PaymentStatus.PENDING);
        dto.setItems(List.of(items));
        return dto;
    }

    private InvoiceItemDTO item(String description, int quantity, String unitPrice) {
        InvoiceItemDTO item = new InvoiceItemDTO();
        item.setDescription(description);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }
}