package com.example.invoice.customer_service.controller;

import com.example.invoice.customer_service.dto.CustomerRequestDTO;
import com.example.invoice.customer_service.dto.CustomerResponseDTO;
import com.example.invoice.customer_service.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponseDTO> create(
            @Valid @RequestBody CustomerRequestDTO dto,
            UriComponentsBuilder uriBuilder) {

        CustomerResponseDTO created = customerService.createCustomer(dto);
        return ResponseEntity
                .created(uriBuilder.path("/api/customers/{id}")
                        .buildAndExpand(created.getCustomerId()).toUri())
                .body(created);
    }

    @GetMapping("/{customerId}")
    public CustomerResponseDTO get(@PathVariable Long customerId) {
        return customerService.getCustomer(customerId);
    }

    @GetMapping
    public Page<CustomerResponseDTO> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "customerId", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.listCustomers(search, pageable);
    }

    @PutMapping("/{customerId}")
    public CustomerResponseDTO update(@PathVariable Long customerId,
            @Valid @RequestBody CustomerRequestDTO dto) {
        return customerService.updateCustomer(customerId, dto);
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> delete(@PathVariable Long customerId) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }
}