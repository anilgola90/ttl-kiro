package com.example.kirotest.service;

import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Ticket;

/**
 * Manages the lifecycle of vector-store embeddings ("chunks") derived from ticket data.
 *
 * <p>The RAG pipeline answers questions strictly from ticket content, so every mutation to a
 * ticket or its comments must be reflected in the vector store to avoid stale embeddings —
 * a correctness (P1) concern. This service encapsulates chunk construction, ingestion, and
 * deletion.
 *
 * <p>Implementations are expected to be resilient: embedding failures must never break the
 * originating ticket operation. Failures are logged and swallowed rather than rethrown
 * (see Requirements 1.8, 6.4, 9.2).
 *
 * <p>Chunking follows the semantic strategy documented in {@code rag-vector-store.md}:
 * one DESCRIPTION chunk per ticket, one COMMENT chunk per comment, and one RESOLUTION chunk
 * for resolved/closed tickets.
 */
public interface EmbeddingService {

    /**
     * Ingests a ticket's DESCRIPTION chunk ({@code title + "\n\n" + description}) into the
     * vector store. Used on ticket creation.
     *
     * @param ticket the ticket to ingest; must have a non-null id
     */
    void ingestTicket(Ticket ticket);

    /**
     * Re-ingests a ticket after a mutation: deletes all existing chunks for the ticket, then
     * re-adds the DESCRIPTION chunk and, when the ticket is RESOLVED or CLOSED with resolution
     * notes present, a RESOLUTION chunk. Implements the upsert pattern for stale-chunk removal.
     *
     * @param ticket the ticket to re-ingest
     */
    void reingestTicket(Ticket ticket);

    /**
     * Ingests a COMMENT chunk ({@code "Comment by <author>: <body>"}) for the given comment.
     * Comment chunks are additive — no deletion of prior chunks occurs.
     *
     * @param comment the comment to ingest; must reference its owning ticket
     */
    void ingestComment(Comment comment);

    /**
     * Removes all chunks associated with the given ticket id, matched via the {@code ticketId}
     * metadata filter. Used before re-ingestion and on ticket deletion.
     *
     * @param ticketId the business id of the ticket whose chunks should be removed
     */
    void deleteChunks(String ticketId);
}
