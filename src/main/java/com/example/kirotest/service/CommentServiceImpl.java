package com.example.kirotest.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.dto.request.AddCommentRequest;
import com.example.kirotest.dto.response.CommentResponse;
import com.example.kirotest.event.CommentAddedEvent;
import com.example.kirotest.exception.TicketNotFoundException;
import com.example.kirotest.repository.CommentRepository;
import com.example.kirotest.repository.TicketRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link CommentService}.
 *
 * <p>Persists a comment against its owning ticket and keeps the ticket's audit timestamp fresh,
 * then decouples the embedding work from the mutating transaction by publishing a
 * {@link CommentAddedEvent} which {@code EmbeddingServiceImpl} consumes asynchronously — so a
 * slow or failing embedding pipeline never blocks or rolls back the comment mutation.
 *
 * <h2>Touching {@code updatedAt}</h2>
 * <p>Adding a comment logically modifies the parent ticket, but a comment insert alone does not
 * trigger the ticket's {@code @LastModifiedDate} auditing. A no-op {@code save} of the ticket
 * re-triggers the {@code AuditingEntityListener}, refreshing {@code updatedAt} so the ticket
 * reflects the most recent activity.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CommentResponse addComment(String ticketId, AddCommentRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        Comment comment = Comment.builder()
                .ticket(ticket)
                .body(request.body())
                .author(request.author())
                .build();

        Comment saved = commentRepository.save(comment);
        log.info("Added comment {} to ticket {}", saved.getId(), ticket.getId());

        // Touch the parent ticket so @LastModifiedDate refreshes updatedAt — a comment insert
        // alone does not re-trigger the ticket's auditing listener.
        ticketRepository.save(ticket);

        eventPublisher.publishEvent(new CommentAddedEvent(saved, ticket));
        return toCommentResponse(saved);
    }

    // ------------------------------------------------------------------
    // Mapping helper — entities are never exposed across the API boundary.
    // ------------------------------------------------------------------

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
