package com.example.kirotest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.kirotest.dto.request.AskRequest;
import com.example.kirotest.dto.response.AskResponse;
import com.example.kirotest.service.RagService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller exposing the Retrieval-Augmented Generation (RAG) "ask" endpoint.
 *
 * <p>Accepts a natural-language question and returns an answer grounded strictly in support
 * ticket data. This controller handles HTTP concerns only; the retrieval-then-generate flow and
 * grounding guardrail live in {@link RagService}. When no ticket chunk clears the similarity
 * threshold, the service returns the fixed no-match phrase rather than a fabricated answer.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final RagService ragService;

    /**
     * Answers a natural-language question using only retrieved ticket context.
     *
     * <p>Delegates to {@link RagService#ask} and returns HTTP 200 with the resulting
     * {@link AskResponse}, whose {@code grounded} flag indicates whether the answer is backed by
     * retrieved ticket data.
     *
     * @param request the validated ask payload carrying the user's question
     * @return HTTP 200 OK carrying the {@link AskResponse}
     */
    @Operation(summary = "Ask a question grounded in ticket data",
            description = "Answers a natural-language question using only retrieved support-ticket "
                    + "context. Returns a fixed no-match phrase when no relevant tickets are found.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer produced (grounded or no-match)"),
            @ApiResponse(responseCode = "400", description = "Validation error (blank or oversized question)"),
            @ApiResponse(responseCode = "500", description = "LLM or embedding failure")
    })
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(ragService.ask(request));
    }
}
