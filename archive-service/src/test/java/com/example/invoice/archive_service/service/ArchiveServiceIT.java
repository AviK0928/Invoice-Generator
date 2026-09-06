package com.example.invoice.archive_service.service;

import com.example.invoice.archive_service.IntegrationTest;
import com.example.invoice.archive_service.dto.ArchiveResponseDTO;
import com.example.invoice.archive_service.exception.ArchivedInvoiceNotFoundException;
import com.example.invoice.archive_service.repository.ArchivedInvoiceItemRepository;
import com.example.invoice.archive_service.repository.ArchivedInvoiceRepository;
import com.example.invoice.common.enums.ArchiveEventType;
import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.common.inbox.ProcessedEventRepository;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.kafka.dto.ArchiveEventDTO;
import com.example.invoice.common.kafka.dto.ArchiveItemDTO;
import com.example.invoice.common.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Archiving, reading back, and unarchiving.
 *
 * The unarchive test is the one that matters: the delete and the event that
 * tells invoice-service to restore have to commit together, or an invoice ends
 * up archived nowhere and active nowhere. That is the failure the outbox was
 * introduced to close, and asserting the row exists after the delete is what
 * proves they share a transaction.
 *
 * Every call passes an event id because saveArchivedInvoice is now
 * inbox-checked. Ids are unique per invoice within a test: reusing one would
 * silently skip the second invoice rather than archiving it.
 */
class ArchiveServiceIT extends IntegrationTest {

    @Autowired
    ArchiveService archiveService;
    @Autowired
    UnarchiveService unarchiveService;
    @Autowired
    ArchivedInvoiceRepository invoiceRepository;
    @Autowired
    ArchivedInvoiceItemRepository itemRepository;
    @Autowired
    OutboxEventRepository outboxRepository;
    @Autowired
    ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void clean() {
        itemRepository.deleteAll();
        invoiceRepository.deleteAll();
        outboxRepository.deleteAll();
        // Without this the inbox outlives the data it was recording, and the
        // second test to use a given event id archives nothing.
        processedEventRepository.deleteAll();
    }

    @Test
    @DisplayName("an archive event is stored and reads back with its items")
    void archiveRoundTrip() {
        archiveService.saveArchivedInvoice(event(1001L), "invoice-service:1001");

        ArchiveResponseDTO archived = archiveService.getArchivedInvoice(1001L);

        assertThat(archived.getName()).isEqualTo("Test Co");
        assertThat(archived.getEventType()).isEqualTo(ArchiveEventType.MANUAL_ARCHIVE);
        // Items are fetched by invoiceId rather than through the @OneToMany:
        // ArchiveMapper sets only the invoiceId column, leaving the
        // archivedInvoice FK null, so ArchivedInvoice.getItems() is empty.
        assertThat(archived.getItems())
                .singleElement()
                .satisfies(i -> assertThat(i.getDescription()).isEqualTo("Widget"));
    }

    @Test
    @DisplayName("an unknown invoice is a domain miss, not an empty result")
    void unknownInvoiceIsRejected() {
        assertThatThrownBy(() -> archiveService.getArchivedInvoice(9999L))
                .isInstanceOf(ArchivedInvoiceNotFoundException.class)
                .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("unarchiving removes the archive and records the event together")
    void unarchiveDeletesAndRecordsInOneTransaction() {
        archiveService.saveArchivedInvoice(event(1001L), "invoice-service:1001");
        assertThat(archiveService.existsByInvoiceId(1001L)).isTrue();

        unarchiveService.unarchiveInvoice(1001L);

        assertThat(invoiceRepository.findByInvoiceId(1001L)).isEmpty();
        assertThat(itemRepository.findByInvoiceId(1001L)).isEmpty();

        // The row exists because the delete and the record share a
        // transaction. If the publish were a direct Kafka send after the
        // delete and it failed, the invoice would be archived nowhere and
        // active nowhere.
        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getAggregateId()).isEqualTo("1001");
                    assertThat(e.getTopic()).isEqualTo(Topics.UNARCHIVE_INVOICES);
                    assertThat(e.getEventType()).isEqualTo(ArchiveEventType.UNARCHIVE.name());
                    assertThat(e.getPublishedAt()).isNull();
                });
    }

    @Test
    @DisplayName("the search filters by customer and date range")
    void searchFilters() {
        archiveService.saveArchivedInvoice(
                event(1001L, 1L, LocalDate.of(2026, 9, 10)), "invoice-service:1001");
        archiveService.saveArchivedInvoice(
                event(1002L, 2L, LocalDate.of(2026, 9, 10)), "invoice-service:1002");
        archiveService.saveArchivedInvoice(
                event(1003L, 1L, LocalDate.of(2026, 12, 1)), "invoice-service:1003");

        var byCustomer = archiveService.listArchived(1L, null, null, PageRequest.of(0, 20));
        assertThat(byCustomer.getContent())
                .extracting(ArchiveResponseDTO::getInvoiceId)
                .containsExactlyInAnyOrder(1001L, 1003L);

        // Every parameter is optional in the query; a null must widen the
        // search rather than match nothing.
        var byDate = archiveService.listArchived(null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), PageRequest.of(0, 20));
        assertThat(byDate.getContent())
                .extracting(ArchiveResponseDTO::getInvoiceId)
                .containsExactlyInAnyOrder(1001L, 1002L);

        assertThat(archiveService.listArchived(null, null, null, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("a redelivered archive event is skipped, not dead-lettered")
    void redeliveryIsSkipped() {
        archiveService.saveArchivedInvoice(event(1001L), "invoice-service:1001");
        archiveService.saveArchivedInvoice(event(1001L), "invoice-service:1001");

        // Before the inbox this threw on uk_archived_invoices_invoice_id and
        // dead-lettered. The archive was correct either way; the cost was an
        // alert for a redelivery that at-least-once delivery makes normal.
        assertThat(invoiceRepository.findAll()).hasSize(1);
        assertThat(itemRepository.findByInvoiceId(1001L)).hasSize(1);
        assertThat(processedEventRepository.count()).isOne();
    }

    @Test
    @DisplayName("an event with no id is processed without a dedup check")
    void missingEventIdStillProcesses() {
        // Publishers not on the outbox send no X-Event-Id header. Those events
        // still have to be processed; they simply get no protection.
        archiveService.saveArchivedInvoice(event(1001L), null);

        assertThat(invoiceRepository.findAll()).hasSize(1);
        assertThat(processedEventRepository.count()).isZero();
    }

    // ------------------------------------------------------------- fixtures

    private ArchiveEventDTO event(Long invoiceId) {
        return event(invoiceId, 1L, LocalDate.of(2026, 9, 10));
    }

    private ArchiveEventDTO event(Long invoiceId, Long customerId, LocalDate date) {
        return ArchiveEventDTO.builder()
                .invoiceId(invoiceId)
                .customerId(customerId)
                .name("Test Co")
                .email("test@example.com")
                .invoiceDate(date)
                .totalAmount(new BigDecimal("20.00"))
                .paymentStatus(PaymentStatus.PENDING)
                .eventType(ArchiveEventType.MANUAL_ARCHIVE)
                .items(List.of(ArchiveItemDTO.builder()
                        .description("Widget")
                        .quantity(2)
                        .unitPrice(new BigDecimal("10.00"))
                        .totalPrice(new BigDecimal("20.00"))
                        .build()))
                .build();
    }
}