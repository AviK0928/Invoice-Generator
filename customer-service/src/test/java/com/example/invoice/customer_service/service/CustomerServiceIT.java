package com.example.invoice.customer_service.service;

import com.example.invoice.common.enums.EventType;
import com.example.invoice.common.kafka.Topics;
import com.example.invoice.common.outbox.OutboxEventRepository;
import com.example.invoice.customer_service.IntegrationTest;
import com.example.invoice.customer_service.dto.CustomerRequestDTO;
import com.example.invoice.customer_service.dto.CustomerResponseDTO;
import com.example.invoice.customer_service.exception.CustomerNotFoundException;
import com.example.invoice.customer_service.exception.DuplicateEmailException;
import com.example.invoice.customer_service.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * customer-service is the origin of customer-events, which invoice-service and
 * export-service both project into local read models. Nothing verified that a
 * customer change actually records an event, so a silent break here would show
 * up two services downstream as stale customer data.
 *
 * The outbox assertions are the point: the row and the event commit together,
 * so a broker outage delays delivery rather than failing the write.
 */
class CustomerServiceIT extends IntegrationTest {

    @Autowired
    CustomerService customerService;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    OutboxEventRepository outboxRepository;

    @BeforeEach
    void clean() {
        customerRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("creating a customer records a CREATED event in the same transaction")
    void createRecordsEvent() {
        CustomerResponseDTO created = customerService.createCustomer(request("Test Co", "test@example.com"));

        assertThat(customerRepository.findById(created.getCustomerId())).isPresent();

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getTopic()).isEqualTo(Topics.CUSTOMER_EVENTS);
                    assertThat(e.getEventType()).isEqualTo(EventType.CREATED.name());
                    assertThat(e.getAggregateId()).isEqualTo(created.getCustomerId().toString());
                    // Consumers project name and email from this payload.
                    assertThat(e.getPayload()).contains("Test Co", "test@example.com");
                    assertThat(e.getPublishedAt()).isNull();
                });
    }

    @Test
    @DisplayName("a duplicate email is rejected before anything is written")
    void duplicateEmailIsRejected() {
        customerService.createCustomer(request("Test Co", "taken@example.com"));
        outboxRepository.deleteAll();

        assertThatThrownBy(() -> customerService.createCustomer(request("Other Co", "taken@example.com")))
                .isInstanceOf(DuplicateEmailException.class);

        // Checked in application code as well as by the unique constraint, so
        // the caller gets a 409 rather than a 500 from a constraint violation.
        assertThat(customerRepository.count()).isOne();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("updating records an UPDATED event with the new values")
    void updateRecordsEvent() {
        Long id = customerService.createCustomer(request("Test Co", "test@example.com")).getCustomerId();
        outboxRepository.deleteAll();

        customerService.updateCustomer(id, request("Renamed Co", "renamed@example.com"));

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getEventType()).isEqualTo(EventType.UPDATED.name());
                    assertThat(e.getPayload()).contains("Renamed Co", "renamed@example.com");
                });
    }

    @Test
    @DisplayName("an update may keep its own email but not take another's")
    void updateEmailUniqueness() {
        Long id = customerService.createCustomer(request("Test Co", "test@example.com")).getCustomerId();
        customerService.createCustomer(request("Other Co", "other@example.com"));

        // existsByEmailAndCustomerIdNot, not existsByEmail: renaming a customer
        // while keeping its address must not collide with itself.
        customerService.updateCustomer(id, request("Renamed Co", "test@example.com"));

        assertThatThrownBy(() -> customerService.updateCustomer(id, request("Test Co", "other@example.com")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("deleting records a DELETED event carrying the deleted values")
    void deleteRecordsEvent() {
        Long id = customerService.createCustomer(request("Test Co", "test@example.com")).getCustomerId();
        outboxRepository.deleteAll();

        customerService.deleteCustomer(id);

        assertThat(customerRepository.findById(id)).isEmpty();

        // Recorded before the delete so the entity's fields are still readable.
        // Consumers need the id to remove their projection; the rest documents
        // what was removed.
        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getEventType()).isEqualTo(EventType.DELETED.name());
                    assertThat(e.getAggregateId()).isEqualTo(id.toString());
                    assertThat(e.getPayload()).contains("test@example.com");
                });
    }

    @Test
    @DisplayName("an unknown customer is a domain miss on every command")
    void unknownCustomerIsRejected() {
        assertThatThrownBy(() -> customerService.getCustomer(9999L))
                .isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> customerService.updateCustomer(9999L, request("X", "x@example.com")))
                .isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> customerService.deleteCustomer(9999L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    @DisplayName("a blank search returns everything rather than nothing")
    void blankSearchDoesNotFilter() {
        customerService.createCustomer(request("Alpha Co", "alpha@example.com"));
        customerService.createCustomer(request("Beta Co", "beta@example.com"));

        // The wildcard is built in the service, and a blank search means "no
        // filter" rather than "%%". Passing the parameter three times inside
        // LOWER() previously left Postgres unable to infer a type for the null
        // case and it failed with "function lower(bytea) does not exist".
        assertThat(customerService.listCustomers("", PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(2);
        assertThat(customerService.listCustomers(null, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(2);
        assertThat(customerService.listCustomers("alpha", PageRequest.of(0, 20)).getContent())
                .singleElement()
                .satisfies(c -> assertThat(c.getName()).isEqualTo("Alpha Co"));
    }

    private CustomerRequestDTO request(String name, String email) {
        return CustomerRequestDTO.builder().name(name).email(email).build();
    }
}