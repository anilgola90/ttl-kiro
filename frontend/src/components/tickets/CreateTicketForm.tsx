"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { createTicket } from "@/lib/api/ticketsApi";
import type { CreateTicketRequest, Priority, Ticket } from "@/lib/types";
import ErrorNotification from "@/components/common/ErrorNotification";

/** Selectable priority values (mirrors the backend {@code Priority} enum). */
const PRIORITY_OPTIONS: Priority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

/**
 * Minimal create-ticket form.
 *
 * Collects title, description, priority, and (optional) assignee, then posts them via
 * {@link createTicket} using a react-query mutation. On success it navigates to the
 * newly created ticket's detail page. Any error is surfaced through
 * {@link ErrorNotification} rather than thrown, so the user stays on the form and can
 * retry.
 */
export default function CreateTicketForm() {
  const router = useRouter();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<Priority>("MEDIUM");
  const [assignee, setAssignee] = useState("");

  const mutation = useMutation<Ticket, Error, CreateTicketRequest>({
    mutationFn: createTicket,
    onSuccess: (ticket) => {
      // Navigate to the detail page for the ticket that was just created.
      router.push(`/tickets/${ticket.id}`);
    },
  });

  /** Build the request payload and trigger the create mutation. */
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedAssignee = assignee.trim();
    const payload: CreateTicketRequest = {
      title: title.trim(),
      description: description.trim(),
      priority,
      // Omit assignee entirely when blank so the ticket stays unassigned.
      ...(trimmedAssignee ? { assignee: trimmedAssignee } : {}),
    };
    mutation.mutate(payload);
  }

  return (
    <form
      onSubmit={handleSubmit}
      style={{ display: "flex", flexDirection: "column", gap: "0.75rem", maxWidth: "40rem" }}
    >
      <ErrorNotification
        message={mutation.isError ? mutation.error.message : null}
      />

      <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
        <label htmlFor="create-ticket-title">Title</label>
        <input
          id="create-ticket-title"
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
        <label htmlFor="create-ticket-description">Description</label>
        <textarea
          id="create-ticket-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={5}
          required
        />
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
        <label htmlFor="create-ticket-priority">Priority</label>
        <select
          id="create-ticket-priority"
          value={priority}
          onChange={(e) => setPriority(e.target.value as Priority)}
        >
          {PRIORITY_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
        <label htmlFor="create-ticket-assignee">Assignee</label>
        <input
          id="create-ticket-assignee"
          type="text"
          value={assignee}
          placeholder="Leave blank to leave unassigned"
          onChange={(e) => setAssignee(e.target.value)}
        />
      </div>

      <button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? "Creating…" : "Create Ticket"}
      </button>
    </form>
  );
}
