package com.example.kirotest.event;

import com.example.kirotest.domain.Ticket;

/**
 * Domain event published immediately after a new {@link Ticket} has been persisted by
 * {@code TicketServiceImpl.createTicket(...)}.
 *
 * <p><strong>When published:</strong> once the ticket-creation transaction has committed the
 * new entity, so consumers observe a fully persisted ticket.
 *
 * <p><strong>Who consumes it:</strong> {@code EmbeddingServiceImpl} listens for this event via
 * an asynchronous {@code @EventListener}. On receipt it builds the {@code DESCRIPTION} chunk
 * ({@code "[title]\n\n[description]"}), embeds it, and stores the resulting vector in the
 * {@code VectorStore}. Publishing this as a Spring {@code ApplicationEvent} decouples the
 * ticket mutation from the (potentially slow, failure-prone) embedding work so the create
 * operation always succeeds independently of the embedding pipeline.
 *
 * <p>Since Spring Framework 4.2 any object may be published as an application event, so this
 * record does not need to extend {@code ApplicationEvent}.
 *
 * @param ticket the newly created ticket to be ingested
 */
public record TicketCreatedEvent(Ticket ticket) {
}
