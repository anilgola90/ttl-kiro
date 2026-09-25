"use client";

import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";

import { addComment } from "@/lib/api/commentsApi";
import type { AddCommentRequest, Comment } from "@/lib/types";
import ErrorNotification from "@/components/common/ErrorNotification";

/** Props for {@link AddCommentForm}. */
export interface AddCommentFormProps {
  /** Identifier of the ticket to comment on (e.g. `"TKT-1001"`). */
  ticketId: string;
  /** Invoked with the newly created comment after a successful submission. */
  onAdded: (comment: Comment) => void;
}

/**
 * A form for adding a comment to a ticket.
 *
 * Collects a comment `body` and `author`, then issues a `POST .../comments` request via
 * react-query. On success (HTTP 201) the created comment is forwarded to
 * {@link AddCommentFormProps.onAdded} and the form is cleared. On error the backend
 * message is surfaced through {@link ErrorNotification} and the entered values are kept
 * so the user can retry without re-typing. The submit button is disabled while a request
 * is in flight or when either field is blank.
 */
export default function AddCommentForm({
  ticketId,
  onAdded,
}: AddCommentFormProps) {
  const [body, setBody] = useState("");
  const [author, setAuthor] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (req: AddCommentRequest) => addComment(ticketId, req),
    onSuccess: (comment) => {
      setErrorMessage(null);
      onAdded(comment);
      // Clear the form only after a successful create.
      setBody("");
      setAuthor("");
    },
    onError: (error: unknown) => {
      setErrorMessage(error instanceof Error ? error.message : String(error));
    },
  });

  const canSubmit =
    body.trim() !== "" && author.trim() !== "" && !mutation.isPending;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit) return;
    mutation.mutate({ body, author });
  };

  return (
    <form onSubmit={handleSubmit}>
      <ErrorNotification
        message={errorMessage}
        onDismiss={() => setErrorMessage(null)}
      />
      <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
        <label htmlFor="add-comment-author">Author</label>
        <input
          id="add-comment-author"
          type="text"
          value={author}
          onChange={(event) => setAuthor(event.target.value)}
          disabled={mutation.isPending}
        />
        <label htmlFor="add-comment-body">Comment</label>
        <textarea
          id="add-comment-body"
          value={body}
          rows={3}
          onChange={(event) => setBody(event.target.value)}
          disabled={mutation.isPending}
        />
        <button type="submit" disabled={!canSubmit}>
          {mutation.isPending ? "Adding\u2026" : "Add comment"}
        </button>
      </div>
    </form>
  );
}
