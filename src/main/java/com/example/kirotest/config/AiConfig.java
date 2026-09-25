package com.example.kirotest.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Central AI-related Spring configuration.
 *
 * <p>This class deliberately does <strong>not</strong> declare
 * {@code EmbeddingModel}, {@code ChatModel} or {@code PGVectorStore} beans.
 * The Spring AI starters ({@code spring-ai-starter-model-openai},
 * {@code spring-ai-starter-model-ollama} and
 * {@code spring-ai-starter-vector-store-pgvector}) auto-configure those beans
 * from {@code application.yml} according to the active profile
 * (OpenAI in {@code prod}, Ollama in {@code dev}). Declaring them here would
 * conflict with that auto-configuration.
 *
 * <p>Its sole responsibility is to enable binding of {@link AppAiProperties} so
 * the tunable AI values (embedding, retrieval, generation) are available as a
 * type-safe, validated bean throughout the application.
 */
@Configuration
@EnableConfigurationProperties(AppAiProperties.class)
public class AiConfig {
    // Spring AI auto-configuration supplies the EmbeddingModel, ChatModel and
    // PGVectorStore beans per active profile — no manual bean definitions here.
}
