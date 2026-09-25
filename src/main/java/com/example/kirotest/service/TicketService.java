package com.example.kirotest.service;

import org.springframework.data.domain.Pageable;

import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.CreateTicketRequest;
import com.example.kirotest.dto.request.UpdateStatusRequest;
import com.example.kirotest.dto.request.UpdateTicketRequest;
import com.example.kirotest.dto.response.PagedResponse;
import com.example.kirotest.dto.response.TicketResponse;
import com.example.kirotest.dto.response.TicketSummaryResponse;

/**
 * Application service defining the ticket lifecycle use cases.
 *
 * <p>This interface is the single business-logic entry point for ticket operations; the
 * controller layer depends only on this abstraction and never on the concrete
 * {@code TicketServiceImpl}. All methods accept and return DTOs — JPA entities are never
 * exposed across this boundary.
 */
public interface TicketService {

    /**
     * Creates a new ticket in status {@code OPEN} with a generated {@code TKT-{number}} id and
     * persists it.
     *
     * @param request the validated creation payload
     * @return the full representation of the newly created ticket
     */
    TicketResponse createTicket(CreateTicketRequest request);

    /**
     * Returns a paginated, optionally filtered list of tickets.
     *
     * <p>When {@code status} is supplied only tickets in that status are returned; when a
     * non-blank {@code search} keyword is supplied only tickets whose title or description
     * contain the keyword (case-insensitive) are returned; when both are supplied the filters
     * are applied conjunctively. A blank or {@code null} keyword is treated as absent.
     *
     * @param status   optional status filter, or {@code null} for no status filter
     * @param search   optional keyword filter, or {@code null}/blank for no keyword filter
     * @param pageable pagination and sort information
     * @return a page of compact ticket summaries wrapped in a pagination envelope
     */
    PagedResponse<TicketSummaryResponse> listTickets(TicketStatus status, String search, Pageable pageable);

    /**
     * Fetches the full detail of a single ticket, including its comments.
     *
     * @param id the ticket identifier
     * @return the full ticket representation
     */
    TicketResponse getTicket(String id);

    /**
     * Partially updates the mutable fields of an existing ticket. Only non-null fields on the
     * request are applied; omitted fields are left unchanged.
     *
     * @param id      the ticket identifier
     * @param request the patch payload
     * @return the full updated ticket representation
     */
    TicketResponse updateTicket(String id, UpdateTicketRequest request);

    /**
     * Transitions a ticket to a new status, enforcing the lifecycle state machine.
     *
     * @param id      the ticket identifier
     * @param request the desired target status and optional resolution notes
     * @return the full updated ticket representation
     */
    TicketResponse updateStatus(String id, UpdateStatusRequest request);
}
