package com.example.kirotest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for adding a comment to a support ticket.
 *
 * <p>Both fields are required and rejected when blank so every comment carries authored
 * content suitable for embedding into the ticket's retrieval context.
 *
 * @param body   the comment text (required, non-blank)
 * @param author the name or identifier of the comment author (required, non-blank)
 */
@Schema(description = "Payload for adding a comment to a ticket")
public record AddCommentRequest(

        @Schema(description = "The comment text", example = "Confirmed the fix in staging.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String body,

        @Schema(description = "Author of the comment", example = "jane.doe",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String author
) {
}
