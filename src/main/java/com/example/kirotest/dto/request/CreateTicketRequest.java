package com.example.kirotest.dto.request;

import com.example.kirotest.domain.Priority;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new support ticket.
 *
 * <p>All required fields are validated at the controller boundary via {@code @Valid}. A
 * blank title or description is rejected with HTTP 400 so tickets always carry meaningful
 * content for downstream embedding and retrieval.
 *
 * @param title       short human-readable summary of the issue (required, max 200 chars)
 * @param description full problem details (required, max 5000 chars)
 * @param priority    business priority for triage (required)
 * @param assignee    optional owner of the ticket; {@code null} leaves it unassigned
 */
@Schema(description = "Payload for creating a new support ticket")
public record CreateTicketRequest(

        @Schema(description = "Short summary of the issue", example = "Payment gateway timeout",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
        @NotBlank
        @Size(max = 200)
        String title,

        @Schema(description = "Full description of the problem",
                example = "Customers report timeouts when submitting card payments after 10s.",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 5000)
        @NotBlank
        @Size(max = 5000)
        String description,

        @Schema(description = "Business priority for triage", example = "HIGH",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Priority priority,

        @Schema(description = "Optional ticket owner", example = "jane.doe")
        String assignee
) {
}
