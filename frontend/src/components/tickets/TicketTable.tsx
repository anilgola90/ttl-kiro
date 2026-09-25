"use client";

import Link from "next/link";
import type { TicketSummary } from "@/lib/types";

/** Props for {@link TicketTable}. */
export interface TicketTableProps {
  /** The tickets to display. When empty, an empty-state message is shown instead. */
  tickets: TicketSummary[];
}

/**
 * Tabular listing of ticket summaries.
 *
 * Renders one row per ticket with the columns id, title, status, priority,
 * assignee, and createdAt. The id cell links to the ticket detail page at
 * `/tickets/{id}` via {@link Link}. When {@link TicketTableProps.tickets} is empty a
 * "No tickets found" message is rendered in place of the table so the caller does not
 * have to special-case the empty result.
 */
export default function TicketTable({ tickets }: TicketTableProps) {
  if (tickets.length === 0) {
    return <p>No tickets found</p>;
  }

  return (
    <table style={{ borderCollapse: "collapse", width: "100%" }}>
      <thead>
        <tr>
          <th style={{ textAlign: "left" }}>ID</th>
          <th style={{ textAlign: "left" }}>Title</th>
          <th style={{ textAlign: "left" }}>Status</th>
          <th style={{ textAlign: "left" }}>Priority</th>
          <th style={{ textAlign: "left" }}>Assignee</th>
          <th style={{ textAlign: "left" }}>Created At</th>
        </tr>
      </thead>
      <tbody>
        {tickets.map((ticket) => (
          <tr key={ticket.id}>
            <td>
              <Link href={`/tickets/${ticket.id}`}>{ticket.id}</Link>
            </td>
            <td>{ticket.title}</td>
            <td>{ticket.status}</td>
            <td>{ticket.priority}</td>
            {/* Unassigned tickets have a null assignee; show a dash placeholder. */}
            <td>{ticket.assignee ?? "—"}</td>
            <td>{ticket.createdAt}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
