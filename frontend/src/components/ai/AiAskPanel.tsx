"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { ask } from "@/lib/api/aiApi";
import type { AskResponse } from "@/lib/types";
import ErrorNotification from "@/components/common/ErrorNotification";
import LoadingSpinner from "@/components/common/LoadingSpinner";
import SourceLinks from "@/components/ai/SourceLinks";

/** Inline message shown when the user submits an empty question. */
const EMPTY_QUESTION_MESSAGE = "Question cannot be empty";

/**
 * Interactive panel for asking natural-language questions grounded in ticket data.
 *
 * Renders a question textarea plus an Ask button. Behaviour:
 * - **Client-side validation**: if the trimmed question is empty, an inline
 *   "{@link EMPTY_QUESTION_MESSAGE}" message is shown and the API is NOT called,
 *   avoiding a redundant round-trip.
 * - **Pending**: while the request is in flight a {@link LoadingSpinner} is shown.
 * - **Success**: the answer text is always displayed. When the response is
 *   `grounded`, the cited {@link SourceLinks} are rendered beneath it; when it is not
 *   grounded, only the answer text is shown (no source links).
 * - **Error**: an {@link ErrorNotification} displays the error message extracted from
 *   the API error envelope.
 *
 * The request is issued through a react-query {@link useMutation} wrapping
 * {@link ask}, so pending/error/success states are derived from the mutation.
 */
export default function AiAskPanel() {
  const [question, setQuestion] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);

  const mutation = useMutation<AskResponse, Error, string>({
    mutationFn: (q: string) => ask({ question: q }),
  });

  /**
   * Validate the question client-side and, when valid, trigger the ask mutation.
   * Empty/whitespace-only questions short-circuit with an inline message.
   */
  function handleAsk() {
    const trimmed = question.trim();
    if (trimmed === "") {
      setValidationError(EMPTY_QUESTION_MESSAGE);
      return;
    }
    setValidationError(null);
    mutation.mutate(trimmed);
  }

  const result = mutation.data;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
      <label htmlFor="ai-question" style={{ fontWeight: 600 }}>
        Ask a question about your tickets
      </label>
      <textarea
        id="ai-question"
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
        rows={4}
        placeholder="e.g. What caused previous payment failures?"
        style={{
          width: "100%",
          padding: "0.5rem",
          borderRadius: "6px",
          border: "1px solid #ccc",
          fontFamily: "inherit",
          fontSize: "1rem",
          resize: "vertical",
        }}
      />

      {/* Inline client-side validation message. */}
      {validationError && (
        <span role="alert" style={{ color: "#842029" }}>
          {validationError}
        </span>
      )}

      <div>
        <button
          type="button"
          onClick={handleAsk}
          disabled={mutation.isPending}
          style={{
            padding: "0.5rem 1.25rem",
            borderRadius: "6px",
            border: "1px solid #0d6efd",
            backgroundColor: "#0d6efd",
            color: "#fff",
            cursor: mutation.isPending ? "not-allowed" : "pointer",
          }}
        >
          Ask
        </button>
      </div>

      {/* Pending state. */}
      {mutation.isPending && <LoadingSpinner label="Asking" />}

      {/* Error state — message comes from the API error envelope. */}
      {mutation.isError && (
        <ErrorNotification message={mutation.error.message} />
      )}

      {/* Success state — always show the answer; show sources only when grounded. */}
      {result && !mutation.isPending && (
        <div>
          <p style={{ whiteSpace: "pre-wrap" }}>{result.answer}</p>
          {result.grounded && <SourceLinks sources={result.sources} />}
        </div>
      )}
    </div>
  );
}
