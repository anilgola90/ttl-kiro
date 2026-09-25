package com.example.kirotest.service;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.example.kirotest.event.CommentAddedEvent;
import com.example.kirotest.event.TicketCreatedEvent;
import com.example.kirotest.event.TicketUpdatedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Asynchronous bridge from ticket/comment domain events to the {@link EmbeddingService}.
 *
 * <p>The embedding work runs off the request thread ({@code @Async}) so a slow or failing
 * embedding pipeline never blocks or rolls back the originating ticket/comment mutation. Each
 * listener simply delegates to the corresponding {@link EmbeddingService} method, which already
 * catches and swallows failures (Requirements 1.8, 4.4, 5.11, 6.4).
 *
 * <p>The listeners live in this dedicated component rather than on {@code EmbeddingServiceImpl}
 * because that implementation is exposed through the {@link EmbeddingService} interface and is
 * therefore backed by a JDK dynamic proxy; {@code @EventListener} methods must be visible on the
 * proxied type. Keeping them here (a concrete, CGLIB-proxied {@code @Component} with no
 * implemented interface) avoids that limitation and keeps event wiring separate from the
 * embedding logic itself.
 */
@Component
@RequiredArgsConstructor
public class TicketEmbeddingEventListener {

    private final EmbeddingService embeddingService;

    /**
     * On {@link TicketCreatedEvent}, ingest the new ticket's DESCRIPTION chunk.
     *
     * @param event the ticket-created event carrying the persisted ticket
     */
    @Async
    @EventListener
    public void onTicketCreated(TicketCreatedEvent event) {
        embeddingService.ingestTicket(event.ticket());
    }

    /**
     * On {@link TicketUpdatedEvent}, re-ingest the ticket (delete + re-add) so the vector store
     * stays consistent with the latest ticket state.
     *
     * @param event the ticket-updated event carrying the updated ticket
     */
    @Async
    @EventListener
    public void onTicketUpdated(TicketUpdatedEvent event) {
        embeddingService.reingestTicket(event.ticket());
    }

    /**
     * On {@link CommentAddedEvent}, ingest the new comment as an additive COMMENT chunk.
     *
     * @param event the comment-added event carrying the new comment and its owning ticket
     */
    @Async
    @EventListener
    public void onCommentAdded(CommentAddedEvent event) {
        embeddingService.ingestComment(event.comment());
    }
}
