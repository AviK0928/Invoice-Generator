package com.example.invoice.invoice_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8082}")
    private String port;

    @Bean
    public OpenAPI invoiceServiceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Invoice Service API")
                        .version("1.0.0")
                        .description("""
                                Invoice lifecycle: creation, retrieval, archival and deletion.

                                **Idempotent creation.** Two requests with identical content
                                resolve to the same invoice rather than creating a duplicate.
                                Content is fingerprinted from the customer, date and line items —
                                payment status and timestamps are excluded, since they are
                                mutable state rather than content.

                                **Errors** follow RFC 9457 Problem Details. Validation failures
                                return 400 with a per-field `errors` object; an unknown customer
                                returns 422, since the request parsed correctly but was
                                semantically wrong.

                                **Events.** Creation and archival publish to Kafka for the
                                export and archive services. Note that an idempotent create
                                short-circuits before publishing.
                                """)
                        .contact(new Contact()
                                .name("AviK0928")
                                .url("https://github.com/AviK0928/Invoice-Generator"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("/").description("Through the gateway"),
                        new Server().url("http://localhost:" + port).description("Direct")));
    }
}