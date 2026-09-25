package com.example.kirotest.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.kirotest.domain.Comment;

/**
 * Spring Data JPA repository for {@link Comment} entities.
 *
 * <p>Comments use a generated {@link UUID} surrogate key since they have no meaningful business
 * identifier of their own; they are always accessed in the context of their owning ticket.
 */
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Returns all comments belonging to a ticket, ordered oldest-first.
     *
     * <p>The chronological ordering (by {@code createdAt} ascending) preserves the natural
     * reading order of a comment thread for display and for RAG chunk ingestion.
     *
     * @param ticketId the business identifier of the owning ticket (e.g. {@code "TKT-1001"})
     * @return the ticket's comments ordered by creation time ascending; empty if none exist
     */
    List<Comment> findByTicketIdOrderByCreatedAtAsc(String ticketId);
}
