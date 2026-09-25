package com.example.kirotest.exception;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based tests for {@link GlobalExceptionHandler} error-envelope correctness.
 *
 * <p>Feature: support-ticket-management, Property 9: error envelope completeness.
 *
 * <p>These tests exercise the handler at the unit level — the handler is instantiated
 * directly and the {@link HttpServletRequest} is mocked with Mockito to return a
 * jqwik-generated request URI. This keeps the property fast (no Spring MVC context) while
 * still driving every mapped handler method through a wide range of generated inputs.
 *
 * <p><b>Property 9 — Error envelope completeness and no stack-trace leakage:</b> for any
 * exception handled by {@link GlobalExceptionHandler}, the returned {@link ErrorResponse}
 * body has a non-null / non-blank {@code error}, {@code message}, and {@code timestamp}, a
 * {@code path} equal to the originating request URI, and its serialized text contains no
 * stack-trace markers ({@code "\n\tat "} or {@code "Caused by:"}).
 *
 * <p><b>Validates: Requirements 8.1, 8.3, 8.4</b>
 */
class ErrorEnvelopeProperties {

    /** Marker that appears in a rendered Java stack-trace frame line. */
    private static final String STACK_FRAME_MARKER = "\n\tat ";

    /** Marker that introduces a chained cause in a rendered stack-trace. */
    private static final String CAUSED_BY_MARKER = "Caused by:";

    /**
     * Builds an {@link HttpServletRequest} mock whose {@code getRequestURI()} returns the
     * supplied path.
     */
    private static HttpServletRequest mockRequest(String path) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    /**
     * Asserts every invariant of Property 9 against a handler result for a given request path.
     *
     * @param response the {@link ResponseEntity} returned by a handler method
     * @param path     the request URI that was mocked into the handler
     */
    private static void assertEnvelopeComplete(ResponseEntity<ErrorResponse> response, String path) {
        assertThat(response).isNotNull();
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();

        // Completeness: error / message / timestamp are present and non-blank where applicable.
        assertThat(body.error()).isNotNull().isNotBlank();
        assertThat(body.message()).isNotNull().isNotBlank();
        assertThat(body.timestamp()).isNotNull();

        // Path fidelity: the envelope echoes the originating request path.
        assertThat(body.path()).isNotNull().isEqualTo(path);

        // No stack-trace leakage: the human-facing text must not contain trace markers.
        String serialized = body.error() + " " + body.message();
        assertThat(serialized)
                .doesNotContain(STACK_FRAME_MARKER)
                .doesNotContain(CAUSED_BY_MARKER);
    }

    /**
     * Property 9 for {@link TicketNotFoundException} (HTTP 404, {@code TICKET_NOT_FOUND}).
     *
     * <p><b>Validates: Requirements 8.1, 8.3, 8.4</b>
     */
    @Property(tries = 100)
    void handleTicketNotFound_forAnyId_producesCompleteEnvelope(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String ticketId,
            @ForAll @NotBlank @StringLength(min = 1, max = 60) String path) {
        // Arrange
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mockRequest(path);
        TicketNotFoundException ex = new TicketNotFoundException(ticketId);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleTicketNotFound(ex, request);

        // Assert
        assertEnvelopeComplete(response, path);
        assertThat(response.getBody().error()).isEqualTo("TICKET_NOT_FOUND");
    }

    /**
     * Property 9 for {@link InvalidTransitionException} (HTTP 422, {@code INVALID_TRANSITION}).
     *
     * <p>jqwik auto-generates all {@link TicketStatus} values for {@code from} and {@code to},
     * covering the transition matrix.
     *
     * <p><b>Validates: Requirements 8.1, 8.3, 8.4</b>
     */
    @Property(tries = 100)
    void handleInvalidTransition_forAnyStatusPair_producesCompleteEnvelope(
            @ForAll TicketStatus from,
            @ForAll TicketStatus to,
            @ForAll @NotBlank @StringLength(min = 1, max = 60) String path) {
        // Arrange
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mockRequest(path);
        InvalidTransitionException ex = new InvalidTransitionException(from, to);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleInvalidTransition(ex, request);

        // Assert
        assertEnvelopeComplete(response, path);
        assertThat(response.getBody().error()).isEqualTo("INVALID_TRANSITION");
    }

    /**
     * Property 9 for {@link LlmException} (HTTP 500, {@code LLM_ERROR}).
     *
     * <p>The exception carries a nested cause; the envelope must still expose only the generic
     * client-safe message with no trace markers leaked from the cause.
     *
     * <p><b>Validates: Requirements 8.1, 8.3, 8.4</b>
     */
    @Property(tries = 100)
    void handleLlm_forAnyMessage_producesCompleteEnvelopeWithoutLeakingCause(
            @ForAll @NotBlank @StringLength(min = 1, max = 80) String message,
            @ForAll @NotBlank @StringLength(min = 1, max = 60) String path) {
        // Arrange
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mockRequest(path);
        LlmException ex = new LlmException(message, new RuntimeException("underlying cause"));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleLlm(ex, request);

        // Assert
        assertEnvelopeComplete(response, path);
        assertThat(response.getBody().error()).isEqualTo("LLM_ERROR");
    }

    /**
     * Property 9 for the catch-all {@link Exception} handler (HTTP 500, {@code INTERNAL_ERROR}).
     *
     * <p><b>Validates: Requirements 8.1, 8.3, 8.4</b>
     */
    @Property(tries = 100)
    void handleUnexpected_forAnyMessage_producesCompleteEnvelope(
            @ForAll @NotBlank @StringLength(min = 1, max = 80) String message,
            @ForAll @NotBlank @StringLength(min = 1, max = 60) String path) {
        // Arrange
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mockRequest(path);
        Exception ex = new RuntimeException(message);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex, request);

        // Assert
        assertEnvelopeComplete(response, path);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
    }
}
