package com.example.kirotest.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.CreateTicketRequest;
import com.example.kirotest.dto.request.UpdateStatusRequest;
import com.example.kirotest.dto.request.UpdateTicketRequest;
import com.example.kirotest.dto.response.CommentResponse;
import com.example.kirotest.dto.response.PagedResponse;
import com.example.kirotest.dto.response.TicketResponse;
import com.example.kirotest.dto.response.TicketSummaryResponse;
import com.example.kirotest.event.TicketCreatedEvent;
import com.example.kirotest.event.TicketUpdatedEvent;
import com.example.kirotest.exception.TicketNotFoundException;
import com.example.kirotest.repository.TicketRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link TicketService}.
 *
 * <p>Coordinates ticket persistence, lifecycle transitions and embedding re-ingestion. The
 * embedding work is decoupled from the mutating transaction by publishing domain events
 * ({@link TicketCreatedEvent}, {@link TicketUpdatedEvent}) which {@code EmbeddingServiceImpl}
 * consumes asynchronously — so a slow or failing embedding pipeline never blocks or rolls back
 * a ticket mutation.
 *
 * <h2>State machine ownership</h2>
 * <p>The {@link TicketStateMachine} is a pure, stateless class with no dependencies, so it is
 * instantiated directly as a private final field rather than injected. This avoids registering
 * an extra bean while keeping the transition rules as the single source of truth for status
 * changes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    /** Prefix applied to the sequence value to build the human-readable ticket id. */
    private static final String ID_PREFIX = "TKT-";

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * The lifecycle state machine. Declared directly (not injected) because it is a plain,
     * dependency-free class and is intentionally not a Spring bean.
     */
    private final TicketStateMachine stateMachine = new TicketStateMachine();

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Long value = ticketRepository.nextIdValue();
        String id = ID_PREFIX + value;

        Ticket ticket = Ticket.builder()
                .id(id)
                .title(request.title())
                .description(request.description())
                .status(TicketStatus.OPEN)
                .priority(request.priority())
                .assignee(request.assignee())
                .build();

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created ticket {}", saved.getId());

        eventPublisher.publishEvent(new TicketCreatedEvent(saved));
        return toTicketResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TicketSummaryResponse> listTickets(
            TicketStatus status, String search, Pageable pageable) {

        boolean hasSearch = StringUtils.hasText(search);
        boolean hasStatus = status != null;

        Page<Ticket> page;
        if (hasStatus && hasSearch) {
            page = ticketRepository.searchByKeywordAndStatus(search, status, pageable);
        } else if (hasSearch) {
            page = ticketRepository.searchByKeyword(search, pageable);
        } else if (hasStatus) {
            page = ticketRepository.findByStatus(status, pageable);
        } else {
            page = ticketRepository.findAll(pageable);
        }

        List<TicketSummaryResponse> data = page.getContent().stream()
                .map(this::toSummary)
                .toList();

        return new PagedResponse<>(
                data,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicket(String id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
        return toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse updateTicket(String id, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        // PATCH semantics: only non-null fields are applied.
        if (request.title() != null) {
            ticket.setTitle(request.title());
        }
        if (request.description() != null) {
            ticket.setDescription(request.description());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.assignee() != null) {
            ticket.setAssignee(request.assignee());
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Updated ticket {}", saved.getId());

        eventPublisher.publishEvent(new TicketUpdatedEvent(saved));
        return toTicketResponse(saved);
    }

    @Override
    @Transactional
    public TicketResponse updateStatus(String id, UpdateStatusRequest request) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        // Delegate validity to the state machine; throws InvalidTransitionException (HTTP 422)
        // when the requested transition is not permitted, leaving the ticket unchanged.
        TicketStatus newStatus = stateMachine.transition(ticket.getStatus(), request.status());
        ticket.setStatus(newStatus);

        if (request.resolutionNotes() != null) {
            ticket.setResolutionNotes(request.resolutionNotes());
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Transitioned ticket {} to {}", saved.getId(), newStatus);

        eventPublisher.publishEvent(new TicketUpdatedEvent(saved));
        return toTicketResponse(saved);
    }

    // ------------------------------------------------------------------
    // Mapping helpers — entities are never exposed across the API boundary.
    // ------------------------------------------------------------------

    /**
     * Maps a {@link Ticket} entity to the full {@link TicketResponse} detail view, including its
     * comments ordered oldest-first (order is guaranteed by the entity's {@code @OrderBy}).
     */
    private TicketResponse toTicketResponse(Ticket ticket) {
        List<CommentResponse> comments = ticket.getComments().stream()
                .map(this::toCommentResponse)
                .toList();

        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getResolutionNotes(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                comments);
    }

    /**
     * Maps a {@link Ticket} entity to the compact {@link TicketSummaryResponse} used in list and
     * search results.
     */
    private TicketSummaryResponse toSummary(Ticket ticket) {
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    /**
     * Maps a {@link Comment} entity to its {@link CommentResponse} projection.
     */
    private CommentResponse toCommentResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getBody(),
                comment.getAuthor(),
                comment.getCreatedAt());
    }
}
