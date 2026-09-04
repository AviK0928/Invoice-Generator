package com.example.invoice.customer_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

        private static final String SECURITY_SCHEME = "bearer-jwt";

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
                                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                                                new SecurityScheme()
                                                                .type(SecurityScheme.Type.HTTP)
                                                                .scheme("bearer")
                                                                .bearerFormat("JWT")
                                                                .description("""
                                                                                Obtain a token from `POST /api/auth/login` on the gateway,
                                                                                then paste the `accessToken` value here (without the
                                                                                `Bearer ` prefix — Swagger adds it).
                                                                                """)))
                                .servers(List.of(
                                                new Server().url("/")
                                                                .description("Through the gateway (authenticated)"),
                                                new Server().url("http://localhost:" + port)
                                                                .description("Direct (no auth)")));
        }
}