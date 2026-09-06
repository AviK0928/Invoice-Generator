package com.example.invoice.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every module's error contract, with all five advices in one context.
 *
 * This is the configuration the per-module @WebMvcTest slices cannot reach:
 * each of those registers exactly one advice, so each module's contract is
 * correct in isolation and four of them were dead in the deployed artifact.
 *
 * The resolver takes the first advice with a MATCHING method, and every advice
 * inherits @ExceptionHandler(Exception.class) from BaseExceptionHandler, which
 * matches everything. Unscoped, the first advice registered answered for the
 * whole application: /api/invoices/99999 returned internal-error while
 * /api/customers/99999 returned customer-not-found.
 *
 * What is asserted is the type URI, not the status. A 404 from the wrong
 * handler is still a 404.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ErrorContractIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mockMvc;
    }

    @Test
    @DisplayName("each module answers its own domain 404, not another module's catch-all")
    void eachModuleOwnsItsErrorContract() throws Exception {
        assertNotFound("/api/customers/99999", "customer-not-found");
        assertNotFound("/api/invoices/99999", "invoice-not-found");
        assertNotFound("/api/archives/99999", "archived-invoice-not-found");
        assertNotFound("/api/exports/invoice/99999", "export-invoice-not-found");
    }

    private void assertNotFound(String path, String type) throws Exception {
        mvc().perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://invoice-generator/errors/" + type));
    }
}