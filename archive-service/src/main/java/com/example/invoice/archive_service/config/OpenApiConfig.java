package com.example.invoice.archive_service.config;

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

    @Value("${server.port:8085}")
    private String port;

    @Bean
    public OpenAPI archiveServiceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Archive Service API")
                        .version("1.0.0")
                        .description("""
                                Archived invoices and unarchive operations.

                                Archives arrive by consuming archive events; unarchiving publishes an event
                                that returns the invoice to active state in invoice-service. Retention and
                                purging were removed — see the engineering log.
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