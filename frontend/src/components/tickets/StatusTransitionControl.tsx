"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";

import { updateStatus } from "@/lib/api/ticketsApi";
import type { Ticket, TicketStatus } from "@/lib/types";
import ErrorNotification from "@/components/common/ErrorNotification";

/**
 * Adjacency map of valid lifecycle transitions.
 *
 * This mirrors the backend `TicketStateMachine` transition table so the UI only
 * ever offers statuses the server will accept. Terminal statuses (`CLOSED`,
 * `CANCELLED`) map to an empty list, disabling the control entirely.
 */
const VALID_NEXT_STATUSES: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["RESOLVED", "CANCELLED"],
  RESOLVED: ["CLOSED"],
  CLOSED: [],
  CANCELLED: [],
};

/** Props for {@link StatusTransitionControl}. */
export interface StatusTransitionControlProps {
  /** Identifier of the ticket whose status may change (e.g. `"TKT-1001"`). */
  ticketId: string;
  /** The ticket's current lifecycle status. */
  currentStatus: TicketStatus;
  /** Invoked with the updated ticket after a successful transition. */
  onUpdated: (ticket: Ticket) => void;
}

/**
 * A status transition control: a dropdown of valid next statuses plus a Confirm button.
 *
 * The available options are derived from {@link StatusTransitionControlProps.currentStatus}
 * using {@link VALID_NEXT_STATUSES}, so terminal statuses render a disabled, empty control.
 * Confirming issues a `PATCH .../status` request via react-query. On success the returned
 * ticket is forwarded to {@link StatusTransitionControlProps.onUpdated}; on error the
 * backend message is surfaced through {@link ErrorNotification} and the current status is
 * left unchanged (the selection is preserved so the user can retry).
 */
export default function StatusTransitionControl({
  ticketId,
  currentStatus,
  onUpdated,
}: StatusTransitionControlProps) {
  const nextStatuses = VALID_NEXT_STATUSES[currentStatus];
  const [selected, setSelected] = useState<TicketStatus | "">(
    nextStatuses[0] ?? "",
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (target: TicketStatus) =>
      updateStatus(ticketId, { status: target }),
    onSuccess: (ticket) => {
      setErrorMessage(null);
      onUpdated(ticket);
    },
    onError: (error: unknown) => {
      // Surface the backend-provided message; leave current status unchanged.
      setErrorMessage(error instanceof Error ? error.message : String(error));
    },
  });

  const hasTransitions = nextStatuses.length > 0;

  const handleConfirm = () => {
    if (selected === "") return;
    mutation.mutate(selected);
  };

  return (
    <div>
      <ErrorNotification
        message={errorMessage}
        onDismiss={() => setErrorMessage(null)}
      />
      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
        <label htmlFor="status-transition-select">Change status:</label>
        <select
          id="status-transition-select"
          value={selected}
          disabled={!hasTransitions || mutation.isPending}
          onChange={(event) => setSelected(event.target.value as TicketStatus)}
        >
          {hasTransitions ? (
            nextStatuses.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))
          ) : (
            <option value="">No transitions available</option>
          )}
        </select>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={!hasTransitions || selected === "" || mutation.isPending}
        >
          {mutation.isPending ? "Updating\u2026" : "Confirm"}
        </button>
      </div>
    </div>
  );
}
