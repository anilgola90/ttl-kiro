package com.example.kirotest.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.example.kirotest.config.AppAiProperties;
import com.example.kirotest.config.AppAiProperties.Embedding;
import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.response.AskResponse;
import com.example.kirotest.exception.LlmException;

/**
 * Unit tests for {@link RagServiceImpl}, focused on the guardrail-before-LLM grounding
 * invariant: the chat model must never be invoked when no ticket chunk clears the similarity
 * threshold, grounded answers must cite the retrieved ticket ids, and chat model failures must
 * surface as an {@link LlmException}.
 *
 * <p>All collaborators ({@link VectorStore}, {@link ChatModel}) are mocked; {@link AppAiProperties}
 * is a real instance so the service reads genuine retrieval/generation config values.
 */
@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    private static final String NO_MATCH_PHRASE =
            "No relevant tickets were found to answer this question.";

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        AppAiProperties props = new AppAiProperties(
                new Embedding("openai", "text-embedding-3-small", null, 1536),
                new AppAiProperties.Retrieval(5, 0.75),
                new AppAiProperties.Generation("gpt-4o-mini", 0.0));
        ragService = new RagServiceImpl(vectorStore, chatModel, props);
    }

    @Test
    void ask_withNoMatchingChunks_returnsNoMatchWithoutCallingLlm() {
        // Arrange
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // Act
        AskResponse response = ragService.ask(new AskRequest("Any unrelated question?"));

        // Assert
        assertThat(response.grounded()).isFalse();
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).isEqualTo(NO_MATCH_PHRASE);
        verifyNoInteractions(chatModel);
    }

    @Test
    void ask_withMatchingChunks_callsLlmAndReturnsGroundedAnswer() {
        // Arrange
        List<Document> chunks = List.of(
                new Document("Payment gateway timed out", Map.of("ticketId", "TKT-1001")),
                new Document("Retry logic added", Map.of("ticketId", "TKT-1023")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(chunks);

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Answer citing TKT-1001"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        // Act
        AskResponse response = ragService.ask(new AskRequest("What caused payment failures?"));

        // Assert
        assertThat(response.grounded()).isTrue();
        assertThat(response.sources()).containsExactly("TKT-1001", "TKT-1023");
        assertThat(response.answer()).isEqualTo("Answer citing TKT-1001");

        // The grounding prompt must carry the retrieved context (ticket ids) to the model.
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents())
                .contains("TKT-1001")
                .contains("TKT-1023");
    }

    @Test
    void ask_whenChatModelThrows_throwsLlmException() {
        // Arrange
        List<Document> chunks = List.of(
                new Document("Payment gateway timed out", Map.of("ticketId", "TKT-1001")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(chunks);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("model down"));

        // Act / Assert
        assertThatThrownBy(() -> ragService.ask(new AskRequest("What caused payment failures?")))
                .isInstanceOf(LlmException.class);
    }
}
