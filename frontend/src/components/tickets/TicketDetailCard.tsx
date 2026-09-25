"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";

import { updateTicket } from "@/lib/api/ticketsApi";
import type { Priority, Ticket, UpdateTicketRequest } from "@/lib/types";
import ErrorNotification from "@/components/common/ErrorNotification";

/** Selectable priority values, mirroring the backend `Priority` enum. */
const PRIORITIES: Priority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

/** Props for {@link TicketDetailCard}. */
export interface TicketDetailCardProps {
  /** The ticket to display and optionally edit. */
  ticket: Ticket;
  /** Invoked with the updated ticket after a successful save. */
  onUpdated: (ticket: Ticket) => void;
}

/** Mutable draft state backing the inline edit form. */
interface EditDraft {
  title: string;
  description: string;
  priority: Priority;
  /** Empty string represents "unassigned". */
  assignee: string;
}

/**
 * Build an {@link UpdateTicketRequest} containing only fields whose draft value differs
 * from the original ticket.
 *
 * Sending only changed fields matches the backend PATCH semantics (a `null`/absent field
 * means "leave unchanged"), avoids clobbering concurrent edits, and keeps the request
 * minimal. The assignee is normalised: a blank draft is treated as `undefined` and only
 * counts as a change when the ticket previously had an assignee.
 *
 * @param original - The ticket as last known from the server.
 * @param draft - The user's current edits.
 * @returns A partial update payload with only the changed fields.
 */
function buildChangedFields(
  original: Ticket,
  draft: EditDraft,
): UpdateTicketRequest {
  const changes: UpdateTicketRequest = {};
  if (draft.title !== original.title) changes.title = draft.title;
  if (draft.description !== original.description) {
    changes.description = draft.description;
  }
  if (draft.priority !== original.priority) changes.priority = draft.priority;

  const draftAssignee = draft.assignee.trim();
  const originalAssignee = original.assignee ?? "";
  if (draftAssignee !== originalAssignee) {
    changes.assignee = draftAssignee;
  }
  return changes;
}

/** Create a fresh draft seeded from the given ticket. */
function draftFromTicket(ticket: Ticket): EditDraft {
  return {
    title: ticket.title,
    description: ticket.description,
    priority: ticket.priority,
    assignee: ticket.assignee ?? "",
  };
}

/**
 * Displays a ticket's full detail with an inline edit mode.
 *
 * In read mode every field is shown. Entering edit mode reveals inputs for the mutable
 * fields (title, description, priority, assignee); read-only fields such as status and
 * timestamps remain static. Saving issues a `PATCH /tickets/{id}` containing only the
 * fields the user actually changed. On success the returned ticket is forwarded to
 * {@link TicketDetailCardProps.onUpdated} and the card returns to read mode. On error the
 * backend message is shown via {@link ErrorNotification} and edit mode is preserved so the
 * user's unsaved input is not lost.
 */
export default function TicketDetailCard({
  ticket,
  onUpdated,
}: TicketDetailCardProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState<EditDraft>(() => draftFromTicket(ticket));
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (req: UpdateTicketRequest) => updateTicket(ticket.id, req),
    onSuccess: (updated) => {
      setErrorMessage(null);
      setIsEditing(false);
      setDraft(draftFromTicket(updated));
      onUpdated(updated);
    },
    onError: (error: unknown) => {
      // Keep edit mode and unsaved input so the user can retry.
      setErrorMessage(error instanceof Error ? error.message : String(error));
    },
  });

  const startEditing = () => {
    setErrorMessage(null);
    setDraft(draftFromTicket(ticket));
    setIsEditing(true);
  };

  const cancelEditing = () => {
    setErrorMessage(null);
    setDraft(draftFromTicket(ticket));
    setIsEditing(false);
  };

  const handleSave = () => {
    const changes = buildChangedFields(ticket, draft);
    // Nothing changed — just leave edit mode without a network round-trip.
    if (Object.keys(changes).length === 0) {
      setIsEditing(false);
      return;
    }
    mutation.mutate(changes);
  };

  return (
    <section>
      <ErrorNotification
        message={errorMessage}
        onDismiss={() => setErrorMessage(null)}
      />

      {isEditing ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
          <label htmlFor="ticket-title">Title</label>
          <input
            id="ticket-title"
            type="text"
            value={draft.title}
            onChange={(e) => setDraft({ ...draft, title: e.target.value })}
            disabled={mutation.isPending}
          />

          <label htmlFor="ticket-description">Description</label>
          <textarea
            id="ticket-description"
            rows={5}
            value={draft.description}
            onChange={(e) =>
              setDraft({ ...draft, description: e.target.value })
            }
            disabled={mutation.isPending}
          />

          <label htmlFor="ticket-priority">Priority</label>
          <select
            id="ticket-priority"
            value={draft.priority}
            onChange={(e) =>
              setDraft({ ...draft, priority: e.target.value as Priority })
            }
            disabled={mutation.isPending}
          >
            {PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {priority}
              </option>
            ))}
          </select>

          <label htmlFor="ticket-assignee">Assignee</label>
          <input
            id="ticket-assignee"
            type="text"
            value={draft.assignee}
            onChange={(e) => setDraft({ ...draft, assignee: e.target.value })}
            disabled={mutation.isPending}
          />

          <div style={{ display: "flex", gap: "0.5rem" }}>
            <button
              type="button"
              onClick={handleSave}
              disabled={mutation.isPending}
            >
              {mutation.isPending ? "Saving\u2026" : "Save"}
            </button>
            <button
              type="button"
              onClick={cancelEditing}
              disabled={mutation.isPending}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: "1rem",
            }}
          >
            <h2 style={{ margin: 0 }}>{ticket.title}</h2>
            <button type="button" onClick={startEditing}>
              Edit
            </button>
          </div>
          <dl>
            <dt>ID</dt>
            <dd>{ticket.id}</dd>

            <dt>Description</dt>
            <dd style={{ whiteSpace: "pre-wrap" }}>{ticket.description}</dd>

            <dt>Status</dt>
            <dd>{ticket.status}</dd>

            <dt>Priority</dt>
            <dd>{ticket.priority}</dd>

            <dt>Assignee</dt>
            <dd>{ticket.assignee ?? "Unassigned"}</dd>

            <dt>Resolution notes</dt>
            <dd style={{ whiteSpace: "pre-wrap" }}>
              {ticket.resolutionNotes ?? "\u2014"}
            </dd>

            <dt>Created</dt>
            <dd>
              <time dateTime={ticket.createdAt}>{ticket.createdAt}</time>
            </dd>

            <dt>Updated</dt>
            <dd>
              <time dateTime={ticket.updatedAt}>{ticket.updatedAt}</time>
            </dd>
          </dl>
        </div>
      )}
    </section>
  );
}
