package com.example.kirotest.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Consistent error envelope returned for all API error responses.
 *
 * <p>Provides a machine-readable {@code error} code for programmatic handling alongside a
 * human-readable {@code message}. Stack traces are never exposed here.
 *
 * @param error     machine-readable error code (e.g. {@code TICKET_NOT_FOUND})
 * @param message   human-readable description of the failure
 * @param timestamp when the error occurred (ISO-8601 UTC)
 * @param path      the request path that produced the error
 */
@Schema(description = "Standard error envelope for failed requests")
public record ErrorResponse(

        @Schema(description = "Machine-readable error code", example = "INVALID_TRANSITION")
        String error,

        @Schema(description = "Human-readable error message", example = "Cannot transition from CLOSED to OPEN")
        String message,

        @Schema(description = "When the error occurred (ISO-8601 UTC)", example = "2025-01-15T10:30:00Z")
        Instant timestamp,

        @Schema(description = "Request path that produced the error", example = "/api/v1/tickets/TKT-1001/status")
        String path
) {
}
