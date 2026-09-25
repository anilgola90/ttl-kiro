package com.example.kirotest.exception;

/**
 * Thrown when an embedding (vector ingestion or deletion) operation fails for a ticket.
 *
 * <p>This exception is <b>internal-only</b>: it is never surfaced directly to API clients.
 * Embedding failures are caught, logged, and swallowed by the asynchronous embedding
 * pipeline so that the originating ticket operation always succeeds. It exists to carry
 * context (ticket ID and underlying cause) for logging and diagnostics.
 */
public class EmbeddingException extends RuntimeException {

    private final String ticketId;

    /**
     * Creates an exception for a failed embedding operation.
     *
     * @param ticketId the identifier of the ticket whose embedding operation failed
     * @param cause    the underlying cause of the failure
     */
    public EmbeddingException(String ticketId, Throwable cause) {
        super("Embedding operation failed for ticket: " + ticketId, cause);
        this.ticketId = ticketId;
    }

    /**
     * @return the identifier of the ticket whose embedding operation failed
     */
    public String getTicketId() {
        return ticketId;
    }
}
