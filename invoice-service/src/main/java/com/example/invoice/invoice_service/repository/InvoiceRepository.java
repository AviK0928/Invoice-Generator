package com.example.invoice.invoice_service.repository;

import com.example.invoice.common.enums.PaymentStatus;
import com.example.invoice.invoice_service.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByArchivedFalseAndInvoiceDateBefore(LocalDate cutoffDate);

    Optional<Invoice> findByContentHash(String contentHash);

    @EntityGraph(attributePaths = "items")
    Optional<Invoice> findWithItemsByInvoiceId(Long invoiceId);

    @Query("""
            SELECT i FROM Invoice i
            WHERE (:customerId IS NULL OR i.customerId = :customerId)
              AND (:paymentStatus IS NULL OR i.paymentStatus = :paymentStatus)
              AND (:archived IS NULL OR i.archived = :archived)
              AND (CAST(:fromDate AS date) IS NULL OR i.invoiceDate >= :fromDate)
              AND (CAST(:toDate   AS date) IS NULL OR i.invoiceDate <= :toDate)
            """)
    Page<Invoice> search(Long customerId,
            PaymentStatus paymentStatus,
            Boolean archived,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable);
}