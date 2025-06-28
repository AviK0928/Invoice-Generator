package com.example.invoice.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("customer-service", r -> r.path("/api/customers/**")
                        .uri("http://localhost:8081"))
                .route("invoice-service", r -> r.path("/api/invoices/**")
                        .uri("http://localhost:8082"))
                .route("export-service", r -> r.path("/api/export/**")
                        .uri("http://localhost:8083"))
                .route("import-service", r -> r.path("/api/import/**")
                        .uri("http://localhost:8084"))
                .route("archive-service", r -> r.path("/api/archive/**")
                        .uri("http://localhost:8085"))
                .build();
    }
}
