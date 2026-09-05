package com.example.invoice.customer_service.controller;

import com.example.invoice.customer_service.exception.CustomerNotFoundException;
import com.example.invoice.customer_service.exception.DuplicateEmailException;
import com.example.invoice.customer_service.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.invoice.customer_service.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The RFC 9457 error contract, as clients see it.
 *
 * Exercises BaseExceptionHandler in common through customer-service's concrete
 * subclass — the arrangement that actually ships. common has no test harness of
 * its own, and testing the abstract class in isolation would not prove the
 * subclass registers its inherited handlers.
 *
 * The nested @SpringBootConfiguration is load-bearing. Without it the
 * bootstrapper finds CustomerServiceApplication, whose explicit
 * 
 * @EnableJpaRepositories survives the @WebMvcTest slice and fails the context
 *                        for want of a DataSource.
 */
@WebMvcTest(controllers = CustomerController.class)
class CustomerControllerErrorContractTest {

    /**
     * Exists only to stop the bootstrapper walking up to
     * CustomerServiceApplication. Two traps, both hit on the way here:
     *
     * Scanning nothing but the advice leaves the controller unregistered, so
     * every request falls through to ResourceHttpRequestHandler and returns
     * the "no endpoint matches that path" 404.
     *
     * Scanning the root package registers CustomerServiceApplication as a
     * component and re-processes its @EnableJpaRepositories, which needs an
     * EntityManagerFactory the slice does not build. @WebMvcTest's
     * TypeExcludeFilter filters scanned components; it does not undo the bean
     * definitions those annotations register.
     *
     * So: scan the two packages that hold web components, and nothing else.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            KafkaAutoConfiguration.class })
    @ComponentScan(basePackageClasses = {
            CustomerController.class,
            GlobalExceptionHandler.class })
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CustomerService customerService;

    @Test
    @DisplayName("a domain not-found is a 404 problem+json with type, title and timestamp")
    void notFoundIsProblemDetail() throws Exception {
        given(customerService.getCustomer(99L))
                .willThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                // The media type is half the contract. A client parsing on
                // application/json alone would not notice this changing.
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.type")
                        .value("https://invoice-generator/errors/customer-not-found"))
                .andExpect(jsonPath("$.detail").value("Customer not found: 99"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("a duplicate email is a 409, not a 500")
    void duplicateEmailIsConflict() throws Exception {
        given(customerService.createCustomer(any()))
                .willThrow(new DuplicateEmailException("taken@example.com"));

        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Test Co","email":"taken@example.com"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate email"))
                .andExpect(jsonPath("$.type")
                        .value("https://invoice-generator/errors/duplicate-email"));
    }

    @Test
    @DisplayName("field validation reports every bad field, keyed by field name")
    void validationListsFields() throws Exception {
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"","email":"not-an-email"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                // The errors map is the part a form UI binds to. Without it the
                // client knows something was invalid but not what.
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("an unparseable body is a 400, not a 500")
    void malformedBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    @DisplayName("a path variable of the wrong type is a 400, not a 500")
    void typeMismatchIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/customers/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value("Parameter 'customerId' has an invalid value."));
    }

    @Test
    @DisplayName("an unexpected exception leaks nothing to the client")
    void unexpectedExceptionIsOpaque() throws Exception {
        given(customerService.getCustomer(1L))
                .willThrow(new IllegalStateException("connection string: user:hunter2@db"));

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                // The whole point of the catch-all: the message is logged
                // server-side and never reaches the response.
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("hunter2"))));
    }
}