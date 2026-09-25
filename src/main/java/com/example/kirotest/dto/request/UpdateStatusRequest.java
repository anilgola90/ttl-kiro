package com.example.kirotest.dto.request;

import com.example.kirotest.domain.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for transitioning a ticket to a new lifecycle status.
 *
 * <p>The target {@code status} is required; the actual transition validity is enforced by
 * the state machine in the service layer, not by bean validation. {@code resolutionNotes}
 * is optional and typically supplied when transitioning to {@code RESOLVED} or
 * {@code CLOSED}.
 *
 * @param status          the desired target status (required)
 * @param resolutionNotes optional notes describing how the ticket was resolved
 */
@Schema(description = "Payload for changing a ticket's lifecycle status")
public record UpdateStatusRequest(

        @Schema(description = "Target lifecycle status", example = "RESOLVED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        TicketStatus status,

        @Schema(description = "Optional notes explaining the resolution",
                example = "Increased gateway timeout to 30s and redeployed.")
        String resolutionNotes
) {
}
