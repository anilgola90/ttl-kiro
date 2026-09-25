package com.example.kirotest.dto.response;

import java.time.Instant;

import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight ticket projection used in list/search results.
 *
 * <p>Omits heavy fields (description, resolution notes, comments) to keep list payloads
 * compact; callers fetch the full {@link TicketResponse} for a single ticket when needed.
 *
 * @param id        ticket identifier in {@code TKT-{number}} format
 * @param title     short summary of the issue
 * @param status    current lifecycle status
 * @param priority  business priority
 * @param assignee  current owner, or {@code null} if unassigned
 * @param createdAt creation timestamp (ISO-8601 UTC)
 * @param updatedAt last-modified timestamp (ISO-8601 UTC)
 */
@Schema(description = "Compact ticket summary for list and search views")
public record TicketSummaryResponse(

        @Schema(description = "Ticket identifier", example = "TKT-1001")
        String id,

        @Schema(description = "Short summary of the issue", example = "Payment gateway timeout")
        String title,

        @Schema(description = "Current lifecycle status", example = "OPEN")
        TicketStatus status,

        @Schema(description = "Business priority", example = "HIGH")
        Priority priority,

        @Schema(description = "Current owner, or null if unassigned", example = "jane.doe")
        String assignee,

        @Schema(description = "Creation timestamp (ISO-8601 UTC)", example = "2025-01-15T10:30:00Z")
        Instant createdAt,

        @Schema(description = "Last-modified timestamp (ISO-8601 UTC)", example = "2025-01-15T12:45:00Z")
        Instant updatedAt
) {
}
