package com.example.kirotest.event;

import com.example.kirotest.domain.Ticket;

/**
 * Domain event published after an existing {@link Ticket} has been mutated — for example a
 * field update ({@code title}, {@code description}, {@code priority}, {@code assignee}) or a
 * status transition (including to {@code RESOLVED}/{@code CLOSED}) by {@code TicketServiceImpl}.
 *
 * <p><strong>When published:</strong> after the update transaction has committed the modified
 * entity, so consumers observe the latest ticket state.
 *
 * <p><strong>Who consumes it:</strong> {@code EmbeddingServiceImpl} listens for this event via
 * an asynchronous {@code @EventListener}. On receipt it re-ingests the ticket: it deletes the
 * stale chunks for the ticket (by {@code ticketId} metadata) and re-embeds the current
 * {@code DESCRIPTION} chunk, plus a {@code RESOLUTION} chunk when the ticket is now
 * {@code RESOLVED}/{@code CLOSED}. This upsert keeps the vector store consistent with the
 * source of truth and prevents stale-embedding correctness bugs. Decoupling via a Spring
 * {@code ApplicationEvent} lets the ticket mutation commit independently of the embedding work.
 *
 * <p>Since Spring Framework 4.2 any object may be published as an application event, so this
 * record does not need to extend {@code ApplicationEvent}.
 *
 * @param ticket the updated ticket to be re-ingested
 */
public record TicketUpdatedEvent(Ticket ticket) {
}
