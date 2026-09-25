package com.example.kirotest.exception;

/**
 * Thrown when a ticket is requested by an ID that does not exist in the system.
 *
 * <p>The {@code GlobalExceptionHandler} maps this exception to HTTP <b>404 Not Found</b>
 * with error code {@code TICKET_NOT_FOUND}, signalling to the client that the referenced
 * resource could not be located.
 */
public class TicketNotFoundException extends RuntimeException {

    private final String ticketId;

    /**
     * Creates an exception for a missing ticket.
     *
     * @param ticketId the identifier of the ticket that could not be found
     */
    public TicketNotFoundException(String ticketId) {
        super("Ticket not found: " + ticketId);
        this.ticketId = ticketId;
    }

    /**
     * @return the identifier of the ticket that could not be found
     */
    public String getTicketId() {
        return ticketId;
    }
}
