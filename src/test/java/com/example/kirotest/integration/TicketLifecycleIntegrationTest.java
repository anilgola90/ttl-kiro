package com.example.kirotest.integration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.kirotest.AbstractIntegrationTest;
import com.example.kirotest.domain.Priority;
import com.example.kirotest.domain.TicketStatus;
import com.example.kirotest.dto.request.AddCommentRequest;
import com.example.kirotest.dto.request.CreateTicketRequest;
import com.example.kirotest.dto.request.UpdateStatusRequest;
import com.example.kirotest.dto.request.UpdateTicketRequest;
import com.example.kirotest.dto.response.CommentResponse;
import com.example.kirotest.dto.response.ErrorResponse;
import com.example.kirotest.dto.response.PagedResponse;
import com.example.kirotest.dto.response.TicketResponse;
import com.example.kirotest.dto.response.TicketSummaryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Full-slice integration tests exercising the ticket lifecycle end-to-end:
 * HTTP → {@code TicketController}/{@code CommentController} → service → repository →
 * a real PostgreSQL + pgvector database provisioned by Testcontainers.
 *
 * <p>Requests are driven through {@link MockMvc} against the fully assembled Spring MVC
 * stack, so these tests validate request binding, JSON (de)serialization, the global
 * exception handler, JPA persistence, and Spring Data auditing together as a single slice.
 *
 * <p>The RAG/embedding collaborators ({@code VectorStore}, {@code EmbeddingModel},
 * {@code ChatModel}) are replaced with {@link MockitoBean} stubs so the application context
 * bootstraps offline without an OpenAI API key. Embedding is driven asynchronously off
 * domain events and its failures are swallowed by design, so mocking these beans keeps
 * ticket operations fully functional while remaining reachable without external services.
 */
@AutoConfigureMockMvc
class TicketLifecycleIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    org.springframework.ai.vectorstore.VectorStore vectorStore;

    @MockitoBean
    org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    @MockitoBean
    org.springframework.ai.chat.model.ChatModel chatModel;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final String TICKETS = "/api/v1/tickets";

    private static final TypeReference<PagedResponse<TicketSummaryResponse>> PAGE_TYPE =
            new TypeReference<>() {
            };

    // ---------------------------------------------------------------------
    // Create + read back (auditing)
    // ---------------------------------------------------------------------

    @Test
    void createTicket_returns201AndPersists() throws Exception {
        // Arrange
        CreateTicketRequest request = new CreateTicketRequest(
                "Payment gateway timeout",
                "Customers report timeouts when submitting card payments after 10s.",
                Priority.HIGH,
                "jane.doe");

        // Act
        MvcResult createResult = mockMvc.perform(post(TICKETS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        // Assert — creation response
        TicketResponse body = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TicketResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.id()).startsWith("TKT-");
        assertThat(body.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(body.title()).isEqualTo(request.title());
        assertThat(body.createdAt()).isNotNull();
        assertThat(body.updatedAt()).isNotNull();

        // Assert — GET returns the same persisted data
        MvcResult getResult = mockMvc.perform(get(TICKETS + "/" + body.id()))
                .andExpect(status().isOk())
                .andReturn();
        TicketResponse got = objectMapper.readValue(
                getResult.getResponse().getContentAsString(), TicketResponse.class);
        assertThat(got).isNotNull();
        assertThat(got.id()).isEqualTo(body.id());
        assertThat(got.title()).isEqualTo(request.title());
        assertThat(got.description()).isEqualTo(request.description());
        assertThat(got.priority()).isEqualTo(Priority.HIGH);
        assertThat(got.assignee()).isEqualTo("jane.doe");
        assertThat(got.createdAt()).isNotNull();
        assertThat(got.updatedAt()).isNotNull();
    }

    // ---------------------------------------------------------------------
    // Listing: status filter
    // ---------------------------------------------------------------------

    @Test
    void listTickets_withStatusFilter_returnsOnlyMatching() throws Exception {
        // Arrange — two OPEN tickets, one moved to IN_PROGRESS
        String open1 = createTicket("Status filter open one", "desc a", Priority.LOW);
        String open2 = createTicket("Status filter open two", "desc b", Priority.MEDIUM);
        String moved = createTicket("Status filter moved", "desc c", Priority.HIGH);
        transition(moved, TicketStatus.IN_PROGRESS);

        // Act
        MvcResult result = mockMvc.perform(get(TICKETS + "?status=OPEN&size=100"))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        PagedResponse<TicketSummaryResponse> page = objectMapper.readValue(
                result.getResponse().getContentAsString(), PAGE_TYPE);
        assertThat(page).isNotNull();
        assertThat(page.data()).allMatch(t -> t.status() == TicketStatus.OPEN);
        List<String> ids = page.data().stream().map(TicketSummaryResponse::id).toList();
        assertThat(ids).contains(open1, open2).doesNotContain(moved);
    }

    // ---------------------------------------------------------------------
    // Listing: keyword search
    // ---------------------------------------------------------------------

    @Test
    void listTickets_withKeywordSearch_returnsMatching() throws Exception {
        // Arrange — distinct titles; search should isolate the unique keyword
        String kafka = createTicket("Kafka consumer lag spike", "brokers overloaded", Priority.HIGH);
        createTicket("Redis eviction storm", "cache thrashing", Priority.MEDIUM);

        // Act
        MvcResult result = mockMvc.perform(get(TICKETS + "?search=Kafka&size=100"))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        PagedResponse<TicketSummaryResponse> page = objectMapper.readValue(
                result.getResponse().getContentAsString(), PAGE_TYPE);
        assertThat(page).isNotNull();
        List<String> ids = page.data().stream().map(TicketSummaryResponse::id).toList();
        assertThat(ids).contains(kafka);
        assertThat(page.data())
                .allMatch(t -> t.title().toLowerCase().contains("kafka"));
    }

    // ---------------------------------------------------------------------
    // Comments ordering
    // ---------------------------------------------------------------------

    @Test
    void getTicketWithComments_returnsCommentsOldestFirst() throws Exception {
        // Arrange
        String id = createTicket("Ticket with comments", "needs discussion", Priority.MEDIUM);
        addComment(id, "First comment", "alice");
        addComment(id, "Second comment", "bob");

        // Act
        MvcResult result = mockMvc.perform(get(TICKETS + "/" + id))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        TicketResponse ticket = objectMapper.readValue(
                result.getResponse().getContentAsString(), TicketResponse.class);
        assertThat(ticket).isNotNull();
        assertThat(ticket.comments()).hasSize(2);
        List<CommentResponse> comments = ticket.comments();
        assertThat(comments.get(0).body()).isEqualTo("First comment");
        assertThat(comments.get(1).body()).isEqualTo("Second comment");
        assertThat(comments.get(0).createdAt())
                .isBeforeOrEqualTo(comments.get(1).createdAt());
    }

    // ---------------------------------------------------------------------
    // State machine: valid transition chain
    // ---------------------------------------------------------------------

    @Test
    void stateMachine_validTransitions_succeed() throws Exception {
        // Arrange
        String id = createTicket("Lifecycle ticket", "walk the happy path", Priority.HIGH);

        // Act + Assert — OPEN → IN_PROGRESS → RESOLVED → CLOSED
        assertThat(transition(id, TicketStatus.IN_PROGRESS).status())
                .isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(transition(id, TicketStatus.RESOLVED).status())
                .isEqualTo(TicketStatus.RESOLVED);
        assertThat(transition(id, TicketStatus.CLOSED).status())
                .isEqualTo(TicketStatus.CLOSED);
    }

    // ---------------------------------------------------------------------
    // State machine: invalid transition
    // ---------------------------------------------------------------------

    @Test
    void stateMachine_invalidTransition_returns422() throws Exception {
        // Arrange — drive the ticket to CLOSED via a valid path
        String id = createTicket("Reopen attempt", "cannot go back", Priority.LOW);
        transition(id, TicketStatus.IN_PROGRESS);
        transition(id, TicketStatus.RESOLVED);
        transition(id, TicketStatus.CLOSED);

        // Act — attempt the illegal CLOSED → OPEN transition
        UpdateStatusRequest request = new UpdateStatusRequest(TicketStatus.OPEN, null);
        MvcResult result = mockMvc.perform(patch(TICKETS + "/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        // Assert
        ErrorResponse error = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(error).isNotNull();
        assertThat(error.error()).isEqualTo("INVALID_TRANSITION");
        assertThat(error.message()).isNotBlank();
        assertThat(error.path()).isEqualTo(TICKETS + "/" + id + "/status");
    }

    // ---------------------------------------------------------------------
    // Partial update
    // ---------------------------------------------------------------------

    @Test
    void partialUpdate_changesOnlyProvidedFields_advancesUpdatedAt() throws Exception {
        // Arrange
        String id = createTicket("Original title", "Original description", Priority.MEDIUM);
        MvcResult originalResult = mockMvc.perform(get(TICKETS + "/" + id))
                .andExpect(status().isOk())
                .andReturn();
        TicketResponse original = objectMapper.readValue(
                originalResult.getResponse().getContentAsString(), TicketResponse.class);
        assertThat(original).isNotNull();

        // Act — patch only the title
        UpdateTicketRequest patch = new UpdateTicketRequest("Updated title", null, null, null);
        MvcResult result = mockMvc.perform(patch(TICKETS + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        TicketResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), TicketResponse.class);
        assertThat(updated).isNotNull();
        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.description()).isEqualTo(original.description());
        assertThat(updated.priority()).isEqualTo(original.priority());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(original.updatedAt());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String createTicket(String title, String description, Priority priority) throws Exception {
        CreateTicketRequest request =
                new CreateTicketRequest(title, description, priority, "tester");
        MvcResult result = mockMvc.perform(post(TICKETS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        TicketResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), TicketResponse.class);
        assertThat(body).isNotNull();
        return body.id();
    }

    private void addComment(String ticketId, String body, String author) throws Exception {
        AddCommentRequest request = new AddCommentRequest(body, author);
        mockMvc.perform(post(TICKETS + "/" + ticketId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private TicketResponse transition(String ticketId, TicketStatus target) throws Exception {
        UpdateStatusRequest request = new UpdateStatusRequest(target, null);
        MvcResult result = mockMvc.perform(patch(TICKETS + "/" + ticketId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        TicketResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), TicketResponse.class);
        assertThat(body).isNotNull();
        return body;
    }
}
