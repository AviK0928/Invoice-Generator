package com.example.invoice.customer_service.repository;

import com.example.invoice.customer_service.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndCustomerIdNot(String email, Long customerId);

    @Query("""
            SELECT c FROM Customer c
            WHERE :search IS NULL
               OR LOWER(c.name)  LIKE :search
               OR LOWER(c.email) LIKE :search
            """)
    Page<Customer> search(String search, Pageable pageable);
}