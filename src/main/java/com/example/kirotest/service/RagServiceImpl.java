package com.example.kirotest.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.kirotest.config.AppAiProperties;
import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.response.AskResponse;
import com.example.kirotest.exception.LlmException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link RagService}.
 *
 * <p><b>Guardrail-before-LLM design.</b> This service enforces the project's core grounding
 * invariant: the chat model is <em>never</em> invoked unless at least one ticket chunk clears the
 * configured similarity threshold. Retrieval happens first; if the vector store returns no
 * matching chunks, the method short-circuits and returns the fixed no-match phrase with an empty
 * {@code sources} list and {@code grounded=false} — <em>without</em> calling the LLM. This
 * completely eliminates the risk of a hallucinated answer being synthesised from empty context,
 * because the model is only ever handed non-empty, retrieved ticket context.
 *
 * <p>When context is available, it is assembled from the retrieved chunks, injected into a strict
 * grounding system prompt (which instructs the model to answer only from the supplied tickets and
 * to cite the {@code ticketId}s used), and passed to the chat model. Both the question and the
 * retrieved chunk ids are logged at {@code INFO} for auditability.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    /** Metadata key under which each chunk's owning ticket id is stored. */
    private static final String TICKET_ID_KEY = "ticketId";

    /** Fixed phrase returned when no relevant ticket chunk clears the similarity threshold. */
    private static final String NO_MATCH_PHRASE =
            "No relevant tickets were found to answer this question.";

    /** Separator used to join individual chunk contexts into a single grounding block. */
    private static final String CONTEXT_SEPARATOR = "\n---\n";

    /**
     * Strict grounding prompt template. {@code {context}} is replaced with the retrieved,
     * ticket-id-prefixed chunk text and {@code {question}} with the user's question.
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a support assistant. Answer ONLY using the ticket context provided below.
            Do NOT use any general knowledge outside of these tickets.
            If the provided context does not contain enough information to answer the question,
            respond exactly with: "No relevant tickets were found to answer this question."
            Always cite the ticketId(s) you used.

            Context:
            {context}

            Question: {question}
            """;

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final AppAiProperties props;

    /**
     * {@inheritDoc}
     *
     * <p>Flow: build a {@link SearchRequest} from configured {@code topK}/{@code
     * similarityThreshold}, run similarity search, log question + retrieved ids, apply the
     * guardrail (empty results → no-match, no LLM call), otherwise build grounded context, call
     * the chat model, and return the cited answer.
     */
    @Override
    public AskResponse ask(AskRequest request) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.question())
                .topK(props.retrieval().topK())
                .similarityThreshold(props.retrieval().similarityThreshold())
                .build();

        List<Document> chunks = vectorStore.similaritySearch(searchRequest);

        // Requirement 10.8 — log the question (truncated) and retrieved chunk ticketIds for audit.
        String question = request.question();
        log.info("RAG ask: question='{}...', retrievedChunks={}",
                question.substring(0, Math.min(200, question.length())),
                extractSources(chunks));

        // GUARDRAIL (Requirement 10.3) — never invoke the LLM with empty context. If no chunk
        // cleared the similarity threshold, return the fixed no-match phrase immediately.
        if (chunks == null || chunks.isEmpty()) {
            return new AskResponse(NO_MATCH_PHRASE, List.of(), false);
        }

        // Build the grounding context: join chunks with a separator, each prefixed with its
        // owning ticketId so the model can cite sources accurately.
        String context = chunks.stream()
                .map(chunk -> "[" + ticketIdOf(chunk) + "] " + chunk.getText())
                .collect(Collectors.joining(CONTEXT_SEPARATOR));

        String promptText = SYSTEM_PROMPT_TEMPLATE
                .replace("{context}", context)
                .replace("{question}", question);

        // Temperature is driven by configuration (props.generation().temperature() == 0.0 for
        // deterministic grounded answers). Building the Prompt from a plain string keeps this
        // provider-agnostic rather than binding to an OpenAI-specific options type.
        Prompt prompt = new Prompt(promptText);

        String answerText;
        try {
            ChatResponse chatResponse = chatModel.call(prompt);
            answerText = chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            throw new LlmException("LLM generation failed", e);
        }

        // POST-GENERATION GUARDRAIL — a chunk may clear the similarity threshold by a thin,
        // coincidental margin (common with weak retrieval matches), causing the LLM to answer
        // "no relevant tickets" even though context was passed. In that case the retrieved
        // chunks did not actually support an answer, so we must NOT report the response as
        // grounded or cite sources — doing so would be a misleading, ungrounded citation.
        // Normalise to the canonical no-match response instead.
        if (answerText != null && answerText.contains(NO_MATCH_PHRASE)) {
            return new AskResponse(NO_MATCH_PHRASE, List.of(), false);
        }

        List<String> sources = extractSources(chunks);
        return new AskResponse(answerText, sources, true);
    }

    /**
     * Extracts the distinct owning ticket ids from the retrieved chunks' metadata, preserving
     * retrieval order.
     *
     * @param chunks retrieved documents (may be {@code null} or empty)
     * @return distinct ticket ids, or an empty list when there are no chunks
     */
    private List<String> extractSources(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(this::ticketIdOf)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Reads the {@code ticketId} metadata value from a chunk as a string.
     */
    private String ticketIdOf(Document chunk) {
        Object ticketId = chunk.getMetadata().get(TICKET_ID_KEY);
        return ticketId == null ? "" : ticketId.toString();
    }
}
