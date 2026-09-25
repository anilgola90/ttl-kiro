package com.example.kirotest;

import java.time.Instant;

import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.request.CreateTicketRequest;

/**
 * Central factory for reusable test objects.
 *
 * <p>Every method returns a fresh, fully-populated instance so tests never share mutable
 * state (per the testing guidelines). Builders are returned where a test may want to tweak
 * a single field before use; concrete objects are returned for DTOs which are immutable
 * records.
 *
 * <p>No method touches persistence — these are pure in-memory domain / DTO objects.
 */
public final class TestFixtures {

    private TestFixtures() {
        // static factory holder — not instantiable
    }

    /**
     * A {@link Ticket} builder pre-populated with sane defaults: an OPEN, HIGH-priority
     * ticket with a title, description and assignee. Callers may override any field before
     * calling {@code build()}.
     *
     * @return a ticket builder with valid defaults
     */
    public static Ticket.TicketBuilder aTicket() {
        Instant now = Instant.now();
        return Ticket.builder()
                .id("TKT-1001")
                .title("Payment gateway timeout")
                .description("Customers report timeouts when submitting card payments after 10s.")
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .assignee("jane.doe")
                .createdAt(now)
                .updatedAt(now);
    }

    /**
     * A {@link Comment} attached to the given ticket, populated with a body, author and
     * creation timestamp.
     *
     * @param ticket the owning ticket the comment belongs to
     * @return a comment for the supplied ticket
     */
    public static Comment aComment(Ticket ticket) {
        return Comment.builder()
                .ticket(ticket)
                .body("Investigating the payment provider's response times.")
                .author("john.smith")
                .createdAt(Instant.now())
                .build();
    }

    /**
     * A valid {@link CreateTicketRequest} that passes all bean-validation constraints.
     *
     * @return a create-ticket request with valid values
     */
    public static CreateTicketRequest aCreateTicketRequest() {
        return new CreateTicketRequest(
                "Payment gateway timeout",
                "Customers report timeouts when submitting card payments after 10s.",
                Priority.HIGH,
                "jane.doe");
    }

    /**
     * A valid {@link AskRequest} carrying a sample natural-language question.
     *
     * @return an ask request with a sample question
     */
    public static AskRequest anAskRequest() {
        return new AskRequest("What caused previous payment failures?");
    }
}
