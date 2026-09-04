package com.example.invoice.customer_service.config;

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

    @Value("${server.port:8081}")
    private String port;

    @Bean
    public OpenAPI customerServiceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Customer Service API")
                        .version("1.0.0")
                        .description("""
                                Customer records and the events they produce.

                                Every change publishes a Kafka event consumed by the invoice, export and
                                archive services, each of which keeps its own local projection. Email is
                                unique; a duplicate returns 409.
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