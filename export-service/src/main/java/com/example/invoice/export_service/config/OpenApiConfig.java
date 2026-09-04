package com.example.invoice.export_service.config;

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

    @Value("${server.port:8083}")
    private String port;

    @Bean
    public OpenAPI exportServiceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Export Service API")
                        .version("1.0.0")
                        .description("""
                                PDF and CSV generation from a read model built by consuming invoice events.

                                Archives are assembled in memory inside a read-only transaction and capped at
                                `export.max-invoices`; exceeding the cap returns 413. Nothing is written to
                                disk — the deployment target has an ephemeral filesystem.
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