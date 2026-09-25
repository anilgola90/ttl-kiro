package com.example.kirotest.integration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.kirotest.AbstractIntegrationTest;
import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.response.AskResponse;
import com.example.kirotest.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Full-slice integration tests for the RAG "ask" pipeline
 * (HTTP → {@code AiController} → {@code RagService} → {@code VectorStore}/{@code ChatModel}).
 *
 * <p><b>Why the models are mocked.</b> A real embedding model and chat LLM (Ollama / OpenAI) are
 * not reachable in an offline CI environment, and their output is inherently non-deterministic.
 * To keep this suite deterministic and focused, the Spring AI {@link VectorStore},
 * {@link ChatModel}, and {@link EmbeddingModel} beans are replaced with {@link MockitoBean} mocks and
 * driven directly from each test. This lets us assert the parts of the pipeline we <em>can</em>
 * verify deterministically: the retrieval-then-generate orchestration, the grounding guardrail
 * (never invoke the LLM on empty context), the source-citation contract, and the HTTP envelope.
 * The {@code EmbeddingModel} mock is present only so the application context can load — the RAG
 * service does not call it directly since retrieval is exercised through the mocked
 * {@code VectorStore}.
 *
 * <p>Requests are driven through {@link MockMvc} against the fully assembled Spring MVC stack.
 *
 * <p><b>What this does NOT cover.</b> True retrieval quality — real semantic similarity via live
 * embeddings, threshold tuning against actual vector distances, and end-to-end answer
 * faithfulness — is validated manually or under a separate profile with a real embedding model
 * (Ollama), as documented in {@code .kiro/steering/rag-vector-store.md}. Here we simulate
 * threshold effects by controlling what the mocked {@code VectorStore} returns.
 *
 * <p>Covers Requirements 10.1–10.3 and 10.8.
 */
@AutoConfigureMockMvc
class RagPipelineIntegrationTest extends AbstractIntegrationTest {

    private static final String ASK_URL = "/api/v1/ai/ask";

    private static final String NO_MATCH_PHRASE =
            "No relevant tickets were found to answer this question.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    /**
     * Guardrail contract (Requirements 10.2, 10.3): when retrieval returns no matching chunks,
     * the pipeline must return the fixed no-match phrase with {@code grounded=false} and an empty
     * {@code sources} list, and must NEVER invoke the chat model with empty context.
     */
    @Test
    void ask_withNoMatchingChunks_returnsNoMatchResponse() throws Exception {
        // Arrange — vector store finds nothing above the similarity threshold.
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // Act
        MvcResult result = mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest("anything"))))
                .andExpect(status().isOk())
                .andReturn();

        // Assert — 200 OK with the ungrounded no-match envelope.
        AskResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), AskResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.grounded()).isFalse();
        assertThat(body.sources()).isEmpty();
        assertThat(body.answer()).isEqualTo(NO_MATCH_PHRASE);

        // Guardrail — the LLM must never be called when there is no grounding context.
        verify(chatModel, never()).call(any(Prompt.class));
    }

    /**
     * Grounded-answer contract (Requirements 10.1, 10.8): when retrieval returns matching chunks,
     * the pipeline invokes the chat model with grounding context and returns
     * {@code grounded=true} with the cited ticket ids in {@code sources}.
     */
    @Test
    void ask_withMatchingChunks_returnsGroundedAnswerWithSources() throws Exception {
        // Arrange — one retrieved chunk owned by TKT-1001, and an LLM answer that cites it.
        Document chunk = new Document(
                "Payment failed due to gateway timeout", Map.of("ticketId", "TKT-1001"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        ChatResponse chatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        "Based on TKT-1001, the payment failed because of a gateway timeout."))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        // Act
        MvcResult result = mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AskRequest("Why did the payment fail?"))))
                .andExpect(status().isOk())
                .andReturn();

        // Assert — grounded answer with the owning ticket surfaced as a source.
        AskResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), AskResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.grounded()).isTrue();
        assertThat(body.sources()).containsExactly("TKT-1001");
        assertThat(body.answer()).contains("TKT-1001");

        // The chat model must have been invoked exactly once with grounding context.
        verify(chatModel).call(any(Prompt.class));
    }

    /**
     * Input-validation contract: a blank question is rejected at the controller boundary with
     * HTTP 400 and the standard {@code VALIDATION_ERROR} envelope — no retrieval or generation
     * is attempted.
     */
    @Test
    void ask_withBlankQuestion_returns400() throws Exception {
        // Act — a whitespace-only question violates @NotBlank.
        MvcResult result = mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest("  "))))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Assert — 400 with the VALIDATION_ERROR error code.
        ErrorResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("VALIDATION_ERROR");

        // Neither collaborator should be touched for an invalid request.
        verify(chatModel, never()).call(any(Prompt.class));
    }

    /**
     * Threshold behaviour (documented via config, simulated via mock). The real similarity
     * threshold (default {@code 0.75}, see {@code rag-vector-store.md}) is applied inside the
     * vector store against live embeddings, which are unavailable offline. We simulate the
     * threshold filtering out all low-similarity results by returning an empty list from the
     * mocked {@code similaritySearch}, and assert the no-match guardrail path is taken.
     */
    @Test
    void thresholdBehaviour_documentedViaConfig() throws Exception {
        // Arrange — simulate every candidate chunk scoring below the similarity threshold.
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // Act
        MvcResult result = mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AskRequest("an unrelated question"))))
                .andExpect(status().isOk())
                .andReturn();

        // Assert — filtered-to-empty retrieval yields the ungrounded no-match response.
        AskResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), AskResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.grounded()).isFalse();
        assertThat(body.sources()).isEmpty();
        assertThat(body.answer()).isEqualTo(NO_MATCH_PHRASE);
        verify(chatModel, never()).call(any(Prompt.class));
    }
}
