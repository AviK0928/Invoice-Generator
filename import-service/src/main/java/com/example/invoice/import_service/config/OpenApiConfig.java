package com.example.invoice.import_service.config;

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

    @Value("${server.port:8084}")
    private String port;

    @Bean
    public OpenAPI importServiceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Import Service API")
                        .version("1.0.0")
                        .description("""
                                Bulk invoice import from CSV.

                                Each invoice is imported in its own transaction, so one bad row does not
                                discard the rest of the file. The response reports per-invoice failures.
                                Uploads are bounded by `spring.servlet.multipart.max-file-size` and
                                `import.max-rows`.
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