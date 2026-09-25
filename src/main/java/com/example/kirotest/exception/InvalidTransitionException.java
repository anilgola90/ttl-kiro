package com.example.kirotest.exception;

import com.example.kirotest.domain.TicketStatus;

/**
 * Thrown by the ticket state machine when a status transition is not permitted by the
 * lifecycle rules (for example, attempting to move a {@code CLOSED} ticket back to
 * {@code OPEN}).
 *
 * <p>The {@code GlobalExceptionHandler} maps this exception to HTTP <b>422 Unprocessable
 * Entity</b> with error code {@code INVALID_TRANSITION}, indicating the request was
 * well-formed but violated a business rule.
 */
public class InvalidTransitionException extends RuntimeException {

    private final TicketStatus from;
    private final TicketStatus to;

    /**
     * Creates an exception describing a rejected transition.
     *
     * @param from the current status of the ticket
     * @param to   the requested target status that is not reachable from {@code from}
     */
    public InvalidTransitionException(TicketStatus from, TicketStatus to) {
        super("Cannot transition from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    /**
     * @return the current status the ticket was in when the transition was attempted
     */
    public TicketStatus getFrom() {
        return from;
    }

    /**
     * @return the target status that was requested but not permitted
     */
    public TicketStatus getTo() {
        return to;
    }
}
