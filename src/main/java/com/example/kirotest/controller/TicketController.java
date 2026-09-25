package com.example.kirotest.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.CreateTicketRequest;
import com.example.kirotest.dto.request.UpdateStatusRequest;
import com.example.kirotest.dto.request.UpdateTicketRequest;
import com.example.kirotest.dto.response.PagedResponse;
import com.example.kirotest.dto.response.TicketResponse;
import com.example.kirotest.dto.response.TicketSummaryResponse;
import com.example.kirotest.service.TicketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller exposing the ticket lifecycle use cases under {@code /api/v1/tickets}.
 *
 * <p>This controller is a thin HTTP adapter: it performs request binding and bean
 * validation ({@code @Valid}) at the boundary, then delegates all business logic to
 * {@link TicketService}. No entity is ever accepted or returned here — only request and
 * response DTOs cross this layer, keeping HTTP concerns cleanly separated from the domain.
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /**
     * Creates a new ticket from the supplied payload.
     *
     * <p>Returns HTTP 201 with a {@code Location} header pointing at the newly created
     * resource, as mandated by the API standards for successful {@code POST} operations.
     *
     * @param request the validated ticket creation payload
     * @return the created ticket wrapped in a 201 response with a {@code Location} header
     */
    @Operation(summary = "Create a new support ticket")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ticket created"),
            @ApiResponse(responseCode = "400", description = "Validation failure")
    })
    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketResponse response = ticketService.createTicket(request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + response.id())).body(response);
    }

    /**
     * Lists tickets with optional status and keyword filters and pagination.
     *
     * <p>{@code page}, {@code size} and {@code sort} query parameters are resolved by Spring
     * into the injected {@link Pageable}. When {@code status} and/or {@code search} are
     * supplied they are applied as conjunctive filters by the service layer.
     *
     * @param status   optional lifecycle status filter, or {@code null} for no status filter
     * @param search   optional keyword filter, or {@code null}/blank for no keyword filter
     * @param pageable pagination and sort information resolved from query parameters
     * @return a 200 response containing the matching page of ticket summaries
     */
    @Operation(summary = "List tickets with optional status/keyword filters and pagination")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching tickets")
    })
    @GetMapping
    public ResponseEntity<PagedResponse<TicketSummaryResponse>> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(ticketService.listTickets(status, search, pageable));
    }

    /**
     * Fetches the full detail of a single ticket, including its comments.
     *
     * @param id the ticket identifier in {@code TKT-{number}} format
     * @return a 200 response containing the full ticket representation
     */
    @Operation(summary = "Get a single ticket by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket found"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(ticketService.getTicket(id));
    }

    /**
     * Partially updates the mutable fields of an existing ticket.
     *
     * <p>Only non-null fields on the request are applied; omitted fields are left unchanged,
     * following PATCH semantics.
     *
     * @param id      the ticket identifier
     * @param request the validated patch payload
     * @return a 200 response containing the updated ticket representation
     */
    @Operation(summary = "Partially update a ticket")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket updated"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<TicketResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateTicketRequest request) {
        return ResponseEntity.ok(ticketService.updateTicket(id, request));
    }

    /**
     * Transitions a ticket to a new lifecycle status.
     *
     * <p>The transition is validated against the ticket state machine in the service layer;
     * an invalid transition results in HTTP 422 via the global exception handler.
     *
     * @param id      the ticket identifier
     * @param request the validated target status and optional resolution notes
     * @return a 200 response containing the updated ticket representation
     */
    @Operation(summary = "Transition a ticket to a new status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status transitioned"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "404", description = "Ticket not found"),
            @ApiResponse(responseCode = "422", description = "Invalid state transition")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(ticketService.updateStatus(id, request));
    }
}
