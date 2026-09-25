package com.example.kirotest.service;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import com.example.kirotest.config.AppAiProperties;
import com.example.kirotest.domain.Comment;
import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.Ticket;
import com.example.kirotest.domain.TicketStatus;

/**
 * Unit tests for {@link EmbeddingServiceImpl}.
 *
 * <p>These tests exercise the semantic chunking, metadata construction, upsert (delete-then-add)
 * re-ingestion pattern, and the resilience contract (embedding failures must never propagate to
 * the caller). Collaborators are mocked; a real {@link AppAiProperties} instance is supplied since
 * it is a plain configuration value object.
 *
 * <p>Validates: Requirements 1.7, 1.8, 4.4, 5.9, 9.1, 9.2, 9.3, 9.4.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Captor
    private ArgumentCaptor<List<Document>> documentsCaptor;

    private EmbeddingServiceImpl service;

    private static final String TICKET_ID = "TKT-1001";
    private static final String TITLE = "Payment gateway timeout";
    private static final String DESCRIPTION = "Checkout fails intermittently with a 504 error.";
    private static final String ASSIGNEE = "jane.doe";
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:30:00Z");

    @BeforeEach
    void setUp() {
        // Real config value object — no behaviour to mock, only holds tuning values.
        AppAiProperties properties = new AppAiProperties(
                new AppAiProperties.Embedding("openai", "text-embedding-3-small", null, 1536),
                new AppAiProperties.Retrieval(5, 0.75),
                new AppAiProperties.Generation("gpt-4o-mini", 0.0));
        service = new EmbeddingServiceImpl(vectorStore, properties);
    }

    // ------------------------------------------------------------------
    // Test fixtures
    // ------------------------------------------------------------------

    private Ticket baseTicket() {
        return Ticket.builder()
                .id(TICKET_ID)
                .title(TITLE)
                .description(DESCRIPTION)
                .status(TicketStatus.OPEN)
                .priority(Priority.HIGH)
                .assignee(ASSIGNEE)
                .createdAt(CREATED_AT)
                .build();
    }

    // ------------------------------------------------------------------
    // ingestTicket
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ingestTicket with valid ticket adds a DESCRIPTION chunk carrying full metadata")
    void ingestTicket_withValidTicket_addsDescriptionChunkWithMetadata() {
        // Arrange
        Ticket ticket = baseTicket();

        // Act
        service.ingestTicket(ticket);

        // Assert
        verify(vectorStore).add(documentsCaptor.capture());
        Document doc = documentsCaptor.getValue().get(0);

        assertThat(doc.getText()).isEqualTo(TITLE + "\n\n" + DESCRIPTION);
        assertThat(doc.getMetadata())
                .containsEntry("ticketId", TICKET_ID)
                .containsEntry("chunkType", "DESCRIPTION")
                .containsEntry("status", "OPEN")
                .containsEntry("priority", "HIGH")
                .containsEntry("assignee", ASSIGNEE)
                .containsEntry("createdAt", CREATED_AT.toString());
    }

    @Test
    @DisplayName("ingestTicket does not propagate exceptions thrown by the vector store")
    void ingestTicket_whenVectorStoreThrows_doesNotPropagate() {
        // Arrange
        doThrow(new RuntimeException("embedding backend down")).when(vectorStore).add(anyList());

        // Act + Assert
        assertThatCode(() -> service.ingestTicket(baseTicket())).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // reingestTicket
    // ------------------------------------------------------------------

    @Test
    @DisplayName("reingestTicket deletes existing chunks before adding the new ones")
    void reingestTicket_deletesChunksThenAdds() {
        // Arrange
        Ticket ticket = baseTicket();

        // Act
        service.reingestTicket(ticket);

        // Assert — delete must occur before add (upsert ordering)
        InOrder inOrder = Mockito.inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(any(Filter.Expression.class));
        inOrder.verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("reingestTicket for a RESOLVED ticket with notes adds both DESCRIPTION and RESOLUTION chunks")
    void reingestTicket_whenResolvedWithNotes_addsResolutionChunk() {
        // Arrange
        Ticket ticket = baseTicket();
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolutionNotes("Increased gateway timeout to 30s and added retry.");

        // Act
        service.reingestTicket(ticket);

        // Assert — two add calls: DESCRIPTION then RESOLUTION
        verify(vectorStore, times(2)).add(documentsCaptor.capture());
        List<List<Document>> addedBatches = documentsCaptor.getAllValues();

        Document descriptionDoc = addedBatches.get(0).get(0);
        Document resolutionDoc = addedBatches.get(1).get(0);

        assertThat(descriptionDoc.getMetadata()).containsEntry("chunkType", "DESCRIPTION");
        assertThat(resolutionDoc.getMetadata()).containsEntry("chunkType", "RESOLUTION");
        assertThat(resolutionDoc.getText()).startsWith("Resolution: ");
    }

    // ------------------------------------------------------------------
    // ingestComment
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ingestComment builds a COMMENT chunk from author and body")
    void ingestComment_buildsCommentChunk() {
        // Arrange
        Ticket ticket = baseTicket();
        Comment comment = Comment.builder()
                .ticket(ticket)
                .author("bob.smith")
                .body("I can reproduce this on staging.")
                .createdAt(CREATED_AT)
                .build();

        // Act
        service.ingestComment(comment);

        // Assert
        verify(vectorStore).add(documentsCaptor.capture());
        Document doc = documentsCaptor.getValue().get(0);

        assertThat(doc.getText()).isEqualTo("Comment by bob.smith: I can reproduce this on staging.");
        assertThat(doc.getMetadata()).containsEntry("chunkType", "COMMENT");
    }

    @Test
    @DisplayName("ingestComment does not propagate exceptions thrown by the vector store")
    void ingestComment_whenVectorStoreThrows_doesNotPropagate() {
        // Arrange
        Ticket ticket = baseTicket();
        Comment comment = Comment.builder()
                .ticket(ticket)
                .author("bob.smith")
                .body("I can reproduce this on staging.")
                .createdAt(CREATED_AT)
                .build();
        doThrow(new RuntimeException("embedding backend down")).when(vectorStore).add(anyList());

        // Act + Assert
        assertThatCode(() -> service.ingestComment(comment)).doesNotThrowAnyException();
    }
}
