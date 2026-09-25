package com.example.kirotest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Type-safe binding for all tunable AI configuration under the {@code app.ai}
 * prefix (embedding, retrieval, and generation settings).
 *
 * <p>The record is annotated with {@link Validated} so Spring Boot fails fast at
 * application startup if any bound value violates its constraint. Keeping these
 * values externalised — rather than hardcoded — allows per-profile overrides
 * (dev/test/prod) without code changes.
 *
 * @param embedding  embedding provider/model settings
 * @param retrieval  vector-store retrieval tuning (top-K, similarity threshold)
 * @param generation chat/generation model settings
 */
@ConfigurationProperties(prefix = "app.ai")
@Validated
public record AppAiProperties(
        @Valid Embedding embedding,
        @Valid Retrieval retrieval,
        @Valid Generation generation
) {

    /**
     * Embedding configuration.
     *
     * @param provider      embedding provider identifier (e.g. {@code openai} or {@code ollama})
     * @param model         embedding model name (e.g. {@code text-embedding-3-small})
     * @param ollamaBaseUrl base URL used only when {@code provider} is {@code ollama}; optional
     * @param dimensions    vector dimensionality of the embedding model; must match the store column
     */
    public record Embedding(
            @NotBlank String provider,
            @NotBlank String model,
            String ollamaBaseUrl,
            @Min(1) int dimensions
    ) {}

    /**
     * Retrieval tuning for similarity search.
     *
     * @param topK                maximum number of chunks to retrieve; bounded 1..100
     * @param similarityThreshold minimum cosine similarity a chunk must meet; bounded 0.0..1.0
     */
    public record Retrieval(
            @Min(1) @Max(100) int topK,
            @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold
    ) {}

    /**
     * Generation (chat) configuration.
     *
     * @param model       chat model name (e.g. {@code gpt-4o-mini})
     * @param temperature sampling temperature; bounded 0.0..2.0 (0.0 for deterministic grounded answers)
     */
    public record Generation(
            @NotBlank String model,
            @DecimalMin("0.0") @DecimalMax("2.0") double temperature
    ) {}
}
