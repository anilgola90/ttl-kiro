package com.example.kirotest.service;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.kirotest.config.AppAiProperties;
import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based tests for chunk-metadata completeness produced by
 * {@link EmbeddingServiceImpl#ingestTicket(Ticket)}.
 *
 * <p>Feature: support-ticket-management, Property 8: chunk metadata completeness.
 *
 * <p><b>Property 8 — Chunk metadata completeness and correctness:</b> for any ticket ingested,
 * the stored {@link Document} metadata SHALL carry non-null {@code ticketId}, {@code chunkType},
 * {@code status}, {@code priority}, and {@code createdAt}; {@code ticketId} SHALL equal the
 * ticket's id; {@code chunkType} SHALL be {@code DESCRIPTION} for {@code ingestTicket}; and
 * {@code status}/{@code priority} SHALL match the ticket's enum names. The {@code assignee}
 * entry is always present (empty string when the ticket assignee is null).
 *
 * <p>Each try constructs a fresh {@link VectorStore} mock and a fresh service instance so state
 * never leaks between generated cases. A real {@link AppAiProperties} value object is used since
 * it only holds tuning values.
 *
 * <p>Validates: Requirements 1.7, 9.1.
 */
class ChunkMetadataProperties {

    /**
     * For any generated ticket, {@code ingestTicket} produces a single DESCRIPTION {@link Document}
     * whose metadata is complete and internally consistent with the ticket.
     *
     * <p>Validates: Requirements 1.7, 9.1.
     */
    @Property(tries = 100)
    void ingestTicket_alwaysProducesCompleteMetadata(
            @ForAll("nonBlankStrings") String title,
            @ForAll("nonBlankStrings") String description,
            @ForAll TicketStatus status,
            @ForAll Priority priority,
            @ForAll("nullableAssignees") String assignee,
            @ForAll @LongRange(min = 1L, max = 9_999_999L) long idNumber,
            @ForAll @LongRange(min = 0L, max = 4_102_444_800L) long epochSeconds) {

        // Arrange — fresh mock + service per try for isolation.
        VectorStore vectorStore = mock(VectorStore.class);
        AppAiProperties properties = new AppAiProperties(
                new AppAiProperties.Embedding("openai", "text-embedding-3-small", null, 1536),
                new AppAiProperties.Retrieval(5, 0.75),
                new AppAiProperties.Generation("gpt-4o-mini", 0.0));
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(vectorStore, properties);

        String ticketId = "TKT-" + idNumber;
        Instant createdAt = Instant.ofEpochSecond(epochSeconds);
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .title(title)
                .description(description)
                .status(status)
                .priority(priority)
                .assignee(assignee)
                .createdAt(createdAt)
                .build();

        // Act
        service.ingestTicket(ticket);

        // Assert — capture the document handed to the vector store and inspect its metadata.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);

        assertThat(doc.getMetadata())
                .containsKeys("ticketId", "chunkType", "status", "priority", "createdAt", "assignee");

        assertThat(doc.getMetadata().get("ticketId")).isNotNull().isEqualTo(ticketId);
        assertThat(doc.getMetadata().get("chunkType")).isNotNull().isEqualTo("DESCRIPTION");
        assertThat(doc.getMetadata().get("status")).isNotNull().isEqualTo(status.name());
        assertThat(doc.getMetadata().get("priority")).isNotNull().isEqualTo(priority.name());
        assertThat(doc.getMetadata().get("createdAt")).isNotNull().isEqualTo(createdAt.toString());

        // assignee is always present; null on the ticket is normalised to an empty string.
        String expectedAssignee = assignee == null ? "" : assignee;
        assertThat(doc.getMetadata().get("assignee")).isEqualTo(expectedAssignee);
    }

    /**
     * Non-blank strings for required text fields (title, description). Constrains to alphanumeric
     * plus spaces so generated values are always non-blank after trimming.
     */
    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ')
                .ofMinLength(1)
                .ofMaxLength(120)
                .filter(s -> !s.isBlank());
    }

    /**
     * Assignee values that may be null (unassigned ticket) or a non-blank identifier string.
     */
    @Provide
    Arbitrary<String> nullableAssignees() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha()
                .withChars('.', '_')
                .ofMinLength(1)
                .ofMaxLength(40)
                .filter(s -> !s.isBlank());
        return names.injectNull(0.25);
    }
}
