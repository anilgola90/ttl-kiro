package com.example.kirotest.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for the RAG "ask" endpoint.
 *
 * <p>When {@code grounded} is {@code true}, {@code answer} is derived from the cited ticket
 * {@code sources}. When no ticket chunk clears the similarity threshold, {@code grounded} is
 * {@code false}, {@code sources} is empty, and {@code answer} carries the fixed no-match
 * phrase rather than a fabricated response.
 *
 * @param answer   the natural-language answer, or the no-match phrase when ungrounded
 * @param sources  ticket identifiers cited as evidence (empty when ungrounded)
 * @param grounded whether the answer is backed by retrieved ticket data
 */
@Schema(description = "Answer to an ask request, grounded strictly in ticket data")
public record AskResponse(

        @Schema(description = "The generated answer, or the no-match phrase when ungrounded",
                example = "Based on ticket TKT-1001, payment failures were caused by gateway timeouts.")
        String answer,

        @Schema(description = "Ticket identifiers cited as evidence; empty when ungrounded",
                example = "[\"TKT-1001\", \"TKT-1023\"]")
        List<String> sources,

        @Schema(description = "Whether the answer is backed by retrieved ticket data", example = "true")
        boolean grounded
) {
}
