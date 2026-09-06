package com.example.invoice.app.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * One spec for one process, replacing the five per-service ones excluded from
 * the scan. Named ApiDocsConfig rather than OpenApiConfig so it cannot be
 * confused with them — and so a sixth class with that simple name does not
 * appear in a codebase where the other five are excluded by name.
 *
 * The security scheme is what makes Swagger UI usable: without it there is no
 * Authorize button, and every /api call from the page is a 401. Declared
 * globally, so it applies to every operation including /api/auth/login, where
 * it is simply ignored.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Invoice Generator",
                version = "1.0.0",
                description = """
                        Five services deployed as one artifact — see docs/adr/003.
                        Obtain a token from POST /api/auth/login, then use Authorize."""),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class ApiDocsConfig {
}