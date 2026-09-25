package com.example.kirotest.dto.response;

import java.time.Instant;
import java.util.List;

import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Full response view of a support ticket, including its ordered comments.
 *
 * <p>This is the detail projection returned for single-ticket reads and mutating
 * operations. It is a mapped view of the {@code Ticket} entity; the entity itself is never
 * exposed across the API boundary.
 *
 * @param id              ticket identifier in {@code TKT-{number}} format
 * @param title           short summary of the issue
 * @param description     full problem details
 * @param status          current lifecycle status
 * @param priority        business priority
 * @param assignee        current owner, or {@code null} if unassigned
 * @param resolutionNotes notes describing the resolution, or {@code null} if unresolved
 * @param createdAt       creation timestamp (ISO-8601 UTC)
 * @param updatedAt       last-modified timestamp (ISO-8601 UTC)
 * @param comments        comments ordered oldest-first
 */
@Schema(description = "Full ticket detail including comments")
public record TicketResponse(

        @Schema(description = "Ticket identifier", example = "TKT-1001")
        String id,

        @Schema(description = "Short summary of the issue", example = "Payment gateway timeout")
        String title,

        @Schema(description = "Full description of the problem",
                example = "Customers report timeouts when submitting card payments after 10s.")
        String description,

        @Schema(description = "Current lifecycle status", example = "RESOLVED")
        TicketStatus status,

        @Schema(description = "Business priority", example = "HIGH")
        Priority priority,

        @Schema(description = "Current owner, or null if unassigned", example = "jane.doe")
        String assignee,

        @Schema(description = "Notes describing the resolution, or null if unresolved",
                example = "Increased gateway timeout to 30s and redeployed.")
        String resolutionNotes,

        @Schema(description = "Creation timestamp (ISO-8601 UTC)", example = "2025-01-15T10:30:00Z")
        Instant createdAt,

        @Schema(description = "Last-modified timestamp (ISO-8601 UTC)", example = "2025-01-15T12:45:00Z")
        Instant updatedAt,

        @Schema(description = "Comments ordered oldest-first")
        List<CommentResponse> comments
) {
}
