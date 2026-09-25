package com.example.kirotest.dto.request;

import com.example.kirotest.domain.Priority;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request payload for partially updating an existing support ticket.
 *
 * <p>Every field is optional: a {@code null} value signals "do not update this field",
 * enabling PATCH semantics. When a field is present it must satisfy its size constraint —
 * for example a supplied title cannot be an empty string ({@code @Size(min = 1)}), which
 * prevents callers from blanking out required content while still allowing the field to be
 * omitted entirely.
 *
 * @param title       new title, or {@code null} to leave unchanged (1..200 chars when present)
 * @param description new description, or {@code null} to leave unchanged (1..5000 chars when present)
 * @param priority    new priority, or {@code null} to leave unchanged
 * @param assignee    new assignee, or {@code null} to leave unchanged
 */
@Schema(description = "Payload for partially updating a ticket; null fields are left unchanged")
public record UpdateTicketRequest(

        @Schema(description = "New title; omit or null to leave unchanged", example = "Payment gateway timeout",
                minLength = 1, maxLength = 200)
        @Size(min = 1, max = 200)
        String title,

        @Schema(description = "New description; omit or null to leave unchanged",
                example = "Updated repro steps attached.", minLength = 1, maxLength = 5000)
        @Size(min = 1, max = 5000)
        String description,

        @Schema(description = "New priority; omit or null to leave unchanged", example = "CRITICAL")
        Priority priority,

        @Schema(description = "New assignee; omit or null to leave unchanged", example = "john.roe")
        String assignee
) {
}
