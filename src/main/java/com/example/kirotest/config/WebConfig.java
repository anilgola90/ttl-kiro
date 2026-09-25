package com.example.kirotest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the API.
 *
 * <p>Applies an explicit CORS policy to the {@code /api/**} surface. Allowed
 * origins are read from the {@code app.cors.allowed-origins} property (a
 * comma-separated list) rather than using a wildcard, so production deployments
 * expose the API only to trusted front-end origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Comma-separated list of origins permitted to call the API, bound from
     * {@code app.cors.allowed-origins}.
     */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Registers the CORS policy for all {@code /api/**} endpoints using the
     * explicitly configured origins and the standard set of HTTP methods.
     *
     * @param registry the registry to which the mapping is added
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        for (int i = 0; i < origins.length; i++) {
            origins[i] = origins[i].trim();
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
}
