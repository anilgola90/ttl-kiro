package com.example.kirotest.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import com.example.kirotest.config.AppAiProperties;
import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link EmbeddingService} implementation backed by Spring AI's {@link VectorStore}.
 *
 * <p>Chunk text is built as human-readable prose (never raw JSON) following the semantic
 * chunking strategy in {@code rag-vector-store.md}. Each {@link Document} carries a metadata
 * map (ticketId, chunkType, status, priority, assignee, createdAt) that enables filtered
 * retrieval and, critically, filtered deletion during re-ingestion.
 *
 * <p><b>Resilience contract:</b> the vector store is a secondary store. If an embedding
 * operation fails, the originating ticket/comment operation must still succeed. Every public
 * method therefore catches all exceptions, logs them, and does <b>not</b> rethrow
 * (Requirements 1.8, 6.4, 9.2). Stale chunks left behind by a failed delete are logged as a
 * warning so they can be reconciled later.
 *
 * <p>The underlying {@code EmbeddingModel} is invoked implicitly by {@link VectorStore#add}
 * via Spring AI auto-configuration, so it is not injected here.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    /** Chunk-type discriminator stored in document metadata. */
    private static final String CHUNK_TYPE_DESCRIPTION = "DESCRIPTION";
    private static final String CHUNK_TYPE_COMMENT = "COMMENT";
    private static final String CHUNK_TYPE_RESOLUTION = "RESOLUTION";

    /** Metadata key used to correlate and filter chunks belonging to a single ticket. */
    private static final String META_TICKET_ID = "ticketId";

    private final VectorStore vectorStore;
    private final AppAiProperties properties;

    /**
     * {@inheritDoc}
     *
     * <p>Builds the DESCRIPTION chunk from {@code title + "\n\n" + description} and adds it to
     * the vector store. Failures are logged at ERROR and swallowed so ticket creation succeeds
     * regardless (Requirement 1.8).
     */
    @Override
    public void ingestTicket(Ticket ticket) {
        try {
            Document doc = new Document(
                    ticket.getTitle() + "\n\n" + ticket.getDescription(),
                    buildMetadata(ticket, CHUNK_TYPE_DESCRIPTION));
            vectorStore.add(List.of(doc));
            log.info("Ingested DESCRIPTION chunk for ticket {}", ticket.getId());
        } catch (Exception e) {
            log.error("Failed to ingest DESCRIPTION chunk for ticket {}; ticket operation "
                    + "continues without embedding", ticket.getId(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implements the upsert pattern: deletes all existing chunks for the ticket, re-adds the
     * DESCRIPTION chunk, and — when the ticket is {@link TicketStatus#RESOLVED} or
     * {@link TicketStatus#CLOSED} and resolution notes are present — adds a RESOLUTION chunk.
     * Failures are logged at ERROR and swallowed (Requirements 5.11, 9.1).
     */
    @Override
    public void reingestTicket(Ticket ticket) {
        try {
            deleteChunks(ticket.getId());

            Document descriptionDoc = new Document(
                    ticket.getTitle() + "\n\n" + ticket.getDescription(),
                    buildMetadata(ticket, CHUNK_TYPE_DESCRIPTION));
            vectorStore.add(List.of(descriptionDoc));

            boolean resolvedOrClosed = ticket.getStatus() == TicketStatus.RESOLVED
                    || ticket.getStatus() == TicketStatus.CLOSED;
            boolean hasResolutionNotes = ticket.getResolutionNotes() != null
                    && !ticket.getResolutionNotes().isBlank();

            if (resolvedOrClosed && hasResolutionNotes) {
                Document resolutionDoc = new Document(
                        "Resolution: " + ticket.getResolutionNotes(),
                        buildMetadata(ticket, CHUNK_TYPE_RESOLUTION));
                vectorStore.add(List.of(resolutionDoc));
                log.info("Re-ingested DESCRIPTION + RESOLUTION chunks for ticket {}", ticket.getId());
            } else {
                log.info("Re-ingested DESCRIPTION chunk for ticket {}", ticket.getId());
            }
        } catch (Exception e) {
            log.error("Failed to re-ingest chunks for ticket {}; ticket operation continues "
                    + "without embedding", ticket.getId(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a COMMENT chunk ({@code "Comment by <author>: <body>"}) using metadata derived
     * from the comment's owning ticket. Failures are logged at WARN and swallowed so comment
     * creation succeeds regardless (Requirement 6.4).
     */
    @Override
    public void ingestComment(Comment comment) {
        Ticket ticket = comment.getTicket();
        try {
            Document doc = new Document(
                    "Comment by " + comment.getAuthor() + ": " + comment.getBody(),
                    buildMetadata(ticket, CHUNK_TYPE_COMMENT));
            vectorStore.add(List.of(doc));
            log.info("Ingested COMMENT chunk for ticket {}", ticket != null ? ticket.getId() : "<unknown>");
        } catch (Exception e) {
            log.warn("Failed to ingest COMMENT chunk for ticket {}; comment operation continues "
                    + "without embedding", ticket != null ? ticket.getId() : "<unknown>", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deletes chunks via a metadata filter expression on {@code ticketId}. A failure here is
     * non-fatal: it is logged at WARN noting that stale chunks may remain (a reconcilable
     * correctness concern) and the caller continues (Requirement 9.2).
     */
    @Override
    public void deleteChunks(String ticketId) {
        try {
            Filter.Expression expression = new FilterExpressionBuilder()
                    .eq(META_TICKET_ID, ticketId)
                    .build();
            vectorStore.delete(expression);
            log.debug("Deleted existing chunks for ticket {}", ticketId);
        } catch (Exception e) {
            log.warn("Failed to delete chunks for ticket {}; stale chunks may remain", ticketId, e);
        }
    }

    /**
     * Builds the metadata map attached to every {@link Document} for a ticket-derived chunk.
     *
     * <p>Null-valued fields (notably {@code assignee}) are normalised to an empty string so the
     * metadata schema stays uniform across chunks, which keeps downstream filtering predictable.
     *
     * @param ticket    the ticket the chunk is derived from
     * @param chunkType one of {@code DESCRIPTION}, {@code COMMENT}, or {@code RESOLUTION}
     * @return a mutable metadata map keyed by ticketId, chunkType, status, priority, assignee,
     *         and createdAt
     */
    private Map<String, Object> buildMetadata(Ticket ticket, String chunkType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_TICKET_ID, ticket.getId());
        metadata.put("chunkType", chunkType);
        metadata.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : "");
        metadata.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : "");
        metadata.put("assignee", Optional.ofNullable(ticket.getAssignee()).orElse(""));
        metadata.put("createdAt", ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : "");
        return metadata;
    }
}
