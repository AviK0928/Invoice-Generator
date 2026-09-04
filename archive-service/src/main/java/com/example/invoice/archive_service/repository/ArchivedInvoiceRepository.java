package com.example.invoice.archive_service.repository;

import com.example.invoice.archive_service.entity.ArchivedInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ArchivedInvoiceRepository extends JpaRepository<ArchivedInvoice, Long> {

    List<ArchivedInvoice> findByInvoiceDateBetween(LocalDate start, LocalDate end);

    List<ArchivedInvoice> findByInvoiceDateBefore(LocalDate cutoffDate);

    Optional<ArchivedInvoice> findByInvoiceId(Long invoiceId);

    boolean existsByInvoiceId(Long invoiceId);

    @Query("""
            SELECT a FROM ArchivedInvoice a
            WHERE (:customerId IS NULL OR a.customerId = :customerId)
              AND (CAST(:fromDate AS date) IS NULL OR a.invoiceDate >= :fromDate)
              AND (CAST(:toDate   AS date) IS NULL OR a.invoiceDate <= :toDate)
            """)
    Page<ArchivedInvoice> search(Long customerId, LocalDate fromDate, LocalDate toDate, Pageable pageable);
}