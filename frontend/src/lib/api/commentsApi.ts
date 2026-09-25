/**
 * Typed API client for ticket comments (`/api/v1/tickets/{id}/comments`).
 */

import { apiFetch } from "@/lib/api/client";
import type { AddCommentRequest, Comment } from "@/lib/types";

/**
 * Add a comment to a ticket.
 *
 * @param ticketId - The identifier of the ticket to comment on (e.g. `"TKT-1001"`).
 * @param req - The comment body and author.
 * @returns The newly created {@link Comment}.
 */
export function addComment(
  ticketId: string,
  req: AddCommentRequest,
): Promise<Comment> {
  return apiFetch<Comment>(
    `/tickets/${encodeURIComponent(ticketId)}/comments`,
    {
      method: "POST",
      body: JSON.stringify(req),
    },
  );
}
