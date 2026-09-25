package com.example.kirotest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * SpringDoc / OpenAPI customisation.
 *
 * <p>SpringDoc auto-exposes {@code /v3/api-docs} and {@code /swagger-ui.html};
 * this class only customises the top-level API metadata (title and version)
 * surfaced in the generated documentation.
 *
 * <p>Restricted to the {@code dev} and {@code test} profiles so that Swagger UI
 * is never exposed in production.
 */
@Configuration
@Profile({"dev", "test"})
public class OpenApiConfig {

    /**
     * Defines the OpenAPI document metadata for the Support Ticket Management API.
     *
     * @return the configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI supportTicketOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Support Ticket Management API")
                        .version("v1"));
    }
}
