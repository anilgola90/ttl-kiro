"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { getTicket } from "@/lib/api/ticketsApi";
import type { Comment, Ticket } from "@/lib/types";
import TicketDetailCard from "@/components/tickets/TicketDetailCard";
import StatusTransitionControl from "@/components/tickets/StatusTransitionControl";
import CommentThread from "@/components/tickets/CommentThread";
import AddCommentForm from "@/components/tickets/AddCommentForm";
import LoadingSpinner from "@/components/common/LoadingSpinner";
import ErrorNotification from "@/components/common/ErrorNotification";

/**
 * Ticket detail page (`/tickets/{id}`).
 *
 * Resolves the ticket id from the dynamic route segment and drives a react-query
 * `useQuery` calling {@link getTicket}. While the request is in flight a
 * {@link LoadingSpinner} is shown; a failed request surfaces through
 * {@link ErrorNotification}. On success it composes the detail view:
 *
 * - {@link TicketDetailCard} for viewing/editing the core fields
 * - {@link StatusTransitionControl} for lifecycle transitions
 * - {@link CommentThread} rendering the ticket's comments
 * - {@link AddCommentForm} for appending a new comment
 *
 * Every mutation flows back through the `["ticket", id]` react-query cache using
 * {@link useQueryClient}. Ticket-returning mutations (update, status change) replace the
 * cached ticket directly via `setQueryData`; adding a comment appends to the cached
 * ticket's `comments` list. This keeps the UI in sync without a full page reload, while
 * `invalidateQueries` requests a background refetch to reconcile any server-derived
 * fields (e.g. `updatedAt`).
 */
export default function TicketDetailPage() {
  const params = useParams<{ id: string }>();
  // `useParams` can theoretically yield string | string[]; normalise to a single id.
  const id = Array.isArray(params.id) ? params.id[0] : params.id;

  const queryClient = useQueryClient();
  const queryKey = ["ticket", id] as const;

  const query = useQuery<Ticket>({
    queryKey,
    queryFn: () => getTicket(id),
    // Only fetch once we actually have an id from the route.
    enabled: Boolean(id),
  });

  /**
   * Replace the cached ticket after a mutation that returns the full ticket
   * (detail edit or status transition), then trigger a background refetch to
   * reconcile any server-computed fields.
   */
  function handleTicketUpdated(updated: Ticket) {
    queryClient.setQueryData<Ticket>(queryKey, updated);
    queryClient.invalidateQueries({ queryKey });
  }

  /**
   * Append a newly created comment to the cached ticket so the thread updates
   * immediately, then refetch in the background to stay consistent with the server.
   */
  function handleCommentAdded(comment: Comment) {
    queryClient.setQueryData<Ticket>(queryKey, (current) =>
      current
        ? { ...current, comments: [...current.comments, comment] }
        : current,
    );
    queryClient.invalidateQueries({ queryKey });
  }

  return (
    <main
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
        padding: "1rem",
      }}
    >
      <Link href="/tickets">&larr; Back to tickets</Link>

      {query.isError && (
        <ErrorNotification message={(query.error as Error).message} />
      )}

      {query.isLoading ? (
        <LoadingSpinner label="Loading ticket" />
      ) : query.data ? (
        <>
          <TicketDetailCard
            ticket={query.data}
            onUpdated={handleTicketUpdated}
          />

          <StatusTransitionControl
            ticketId={query.data.id}
            currentStatus={query.data.status}
            onUpdated={handleTicketUpdated}
          />

          <section>
            <h3>Comments</h3>
            <CommentThread comments={query.data.comments} />
            <AddCommentForm
              ticketId={query.data.id}
              onAdded={handleCommentAdded}
            />
          </section>
        </>
      ) : null}
    </main>
  );
}
