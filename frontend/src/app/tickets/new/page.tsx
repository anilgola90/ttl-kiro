"use client";

import Link from "next/link";
import CreateTicketForm from "@/components/tickets/CreateTicketForm";

/**
 * New-ticket page.
 *
 * Thin wrapper that renders the {@link CreateTicketForm}, which handles submission and
 * navigates to the created ticket on success. Provides a back link to the list.
 */
export default function NewTicketPage() {
  return (
    <main style={{ display: "flex", flexDirection: "column", gap: "1rem", padding: "1rem" }}>
      <header style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <h1>New Ticket</h1>
        <Link href="/tickets">Back to tickets</Link>
      </header>

      <CreateTicketForm />
    </main>
  );
}
