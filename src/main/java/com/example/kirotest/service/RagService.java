package com.example.kirotest.service;

import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.response.AskResponse;

/**
 * Retrieval-Augmented Generation (RAG) service that answers natural-language questions
 * grounded <em>strictly</em> in support-ticket data.
 *
 * <p>Implementations follow a single retrieval-then-generate flow: retrieve the most similar
 * ticket chunks from the vector store, and only if relevant chunks are found, hand that context
 * to the chat model to synthesise a cited answer. If no chunk clears the configured similarity
 * threshold, the implementation must return the fixed no-match phrase without ever invoking the
 * language model — the assistant never falls through to general LLM knowledge.
 */
public interface RagService {

    /**
     * Answers a natural-language question using only retrieved ticket context.
     *
     * @param request the ask request carrying the user's question
     * @return an {@link AskResponse} whose {@code grounded} flag indicates whether the answer is
     *         backed by retrieved ticket data; when ungrounded, {@code sources} is empty and
     *         {@code answer} carries the fixed no-match phrase
     */
    AskResponse ask(AskRequest request);
}
