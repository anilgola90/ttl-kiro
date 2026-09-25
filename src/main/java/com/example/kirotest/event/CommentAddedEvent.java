package com.example.kirotest.event;

import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Ticket;

/**
 * Domain event published after a new {@link Comment} has been persisted against its owning
 * {@link Ticket} by {@code CommentServiceImpl.addComment(...)}.
 *
 * <p><strong>When published:</strong> after the comment has been saved and the owning ticket's
 * {@code updatedAt} has been touched, so consumers observe a fully persisted comment together
 * with its parent ticket.
 *
 * <p><strong>Who consumes it:</strong> {@code EmbeddingServiceImpl} listens for this event via
 * an asynchronous {@code @EventListener}. On receipt it builds a {@code COMMENT} chunk
 * ({@code "Comment by [author]: [body]"}), embeds it, and appends it to the {@code VectorStore}
 * with the parent ticket's metadata — no delete is required since a new comment only adds a
 * chunk. The parent {@link Ticket} is carried on the event so the listener can populate chunk
 * metadata ({@code ticketId}, {@code status}, {@code priority}, ...) without an extra lookup.
 * Decoupling via a Spring {@code ApplicationEvent} lets the comment mutation commit
 * independently of the embedding work.
 *
 * <p>Since Spring Framework 4.2 any object may be published as an application event, so this
 * record does not need to extend {@code ApplicationEvent}.
 *
 * @param comment the newly added comment to be ingested
 * @param ticket  the ticket the comment belongs to, used to derive chunk metadata
 */
public record CommentAddedEvent(Comment comment, Ticket ticket) {
}
