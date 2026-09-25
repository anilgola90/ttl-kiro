package com.example.kirotest.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response view of a single comment on a support ticket.
 *
 * <p>This is the outward-facing projection of the {@code Comment} entity; entities are
 * never serialized directly across the API boundary.
 *
 * @param id        unique identifier of the comment
 * @param body      the comment text
 * @param author    author of the comment
 * @param createdAt timestamp the comment was created (ISO-8601 UTC)
 */
@Schema(description = "A comment on a ticket")
public record CommentResponse(

        @Schema(description = "Unique comment identifier",
                example = "3f2504e0-4f89-41d3-9a0c-0305e82c3301")
        UUID id,

        @Schema(description = "The comment text", example = "Confirmed the fix in staging.")
        String body,

        @Schema(description = "Author of the comment", example = "jane.doe")
        String author,

        @Schema(description = "Creation timestamp (ISO-8601 UTC)", example = "2025-01-15T10:30:00Z")
        Instant createdAt
) {
}
