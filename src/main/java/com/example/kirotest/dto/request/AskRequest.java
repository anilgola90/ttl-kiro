package com.example.kirotest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for the RAG "ask" endpoint.
 *
 * <p>The natural-language {@code question} is required and capped at 1000 characters to
 * bound embedding cost and keep the prompt within the model context window. A blank
 * question is rejected with HTTP 400 rather than issuing a wasted retrieval call.
 *
 * @param question the natural-language question to answer using ticket data
 *                 (required, max 1000 chars)
 */
@Schema(description = "Payload for asking a natural-language question grounded in ticket data")
public record AskRequest(

        @Schema(description = "Natural-language question about the tickets",
                example = "What caused previous payment failures?",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 1000)
        @NotBlank
        @Size(max = 1000)
        String question
) {
}
