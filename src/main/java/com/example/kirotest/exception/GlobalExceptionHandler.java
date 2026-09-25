package com.example.kirotest.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.kirotest.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralised REST exception handler that maps application and framework exceptions to a
 * consistent {@link ErrorResponse} envelope with an appropriate HTTP status.
 *
 * <p>Stack traces are never exposed to clients. Each handler builds an {@link ErrorResponse}
 * carrying a machine-readable error code, a human-readable message, a UTC timestamp, and the
 * originating request path. Unexpected and LLM failures are logged at {@code ERROR} without
 * leaking internal details to the caller.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles requests for tickets that do not exist.
     *
     * @param ex      the thrown exception
     * @param request the current HTTP request, used to capture the request path
     * @return HTTP 404 with error code {@code TICKET_NOT_FOUND}
     */
    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTicketNotFound(
            TicketNotFoundException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                "TICKET_NOT_FOUND", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles attempts to perform a status transition that violates the ticket lifecycle rules.
     *
     * @param ex      the thrown exception (message includes current and target status)
     * @param request the current HTTP request, used to capture the request path
     * @return HTTP 422 with error code {@code INVALID_TRANSITION}
     */
    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidTransitionException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                "INVALID_TRANSITION", ex.getMessage(), Instant.now(), request.getRequestURI());
        return ResponseEntity.status(422).body(body);
    }

    /**
     * Handles Bean Validation failures on request bodies, concatenating all field violations
     * into a single message.
     *
     * @param ex      the thrown validation exception
     * @param request the current HTTP request, used to capture the request path
     * @return HTTP 400 with error code {@code VALIDATION_ERROR}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorResponse body = new ErrorResponse(
                "VALIDATION_ERROR", message, Instant.now(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles malformed or unparseable JSON request bodies.
     *
     * @param ex      the thrown exception
     * @param request the current HTTP request, used to capture the request path
     * @return HTTP 400 with error code {@code MALFORMED_JSON}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                "MALFORMED_JSON", "Malformed JSON request body", Instant.now(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles failures originating from the large language model during the RAG ask flow.
     * The underlying cause is logged but never exposed to the client.
     *
     * @param ex      the thrown exception
     * @param request the current HTTP request, used to capture the request path
     * @return HTTP 500 with error code {@code LLM_ERROR} and a generic message
     */
    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ErrorResponse> handleLlm(
            LlmException ex, HttpServletRequest request) {
        log.error("LLM generation failed for request path {}", request.getRequestURI(), ex);
        ErrorResponse body = new ErrorResponse(
                "LLM_ERROR", "An error occurred while generating the answer", Instant.now(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Catch-all handler for any otherwise unhandled exception. The exception is logged and a
     * generic message is returned so internal details are never leaked.
     *
     * @param ex      the thrown exception
     * @param request the current HTTP request, used to capture the request path
     * @return HTTP 500 with error code {@code INTERNAL_ERROR} and a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error for request path {}", request.getRequestURI(), ex);
        ErrorResponse body = new ErrorResponse(
                "INTERNAL_ERROR", "An unexpected error occurred", Instant.now(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
