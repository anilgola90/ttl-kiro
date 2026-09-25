"use client";

import { useState, type FormEvent } from "react";
import type { TicketStatus } from "@/lib/types";

/** The selectable status options, including the "All" (no filter) choice. */
const STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "", label: "All" },
  { value: "OPEN", label: "OPEN" },
  { value: "IN_PROGRESS", label: "IN_PROGRESS" },
  { value: "RESOLVED", label: "RESOLVED" },
  { value: "CLOSED", label: "CLOSED" },
  { value: "CANCELLED", label: "CANCELLED" },
];

/** Props for {@link TicketSearchBar}. */
export interface TicketSearchBarProps {
  /**
   * Invoked when the user submits the search form.
   *
   * @param keyword - The trimmed keyword text (may be an empty string).
   * @param status - The selected status, or `undefined` when "All" is chosen.
   */
  onSearch: (keyword: string, status: string | undefined) => void;
}

/**
 * Search controls for the ticket list: a free-text keyword input plus a status
 * dropdown. Nothing is fired until the form is submitted, so callers only react to
 * deliberate searches rather than every keystroke.
 *
 * The status `<select>` uses an empty string to represent "All"; that is normalised
 * to `undefined` before invoking {@link TicketSearchBarProps.onSearch} so the caller
 * can pass it straight through to the API without a status filter.
 */
export default function TicketSearchBar({ onSearch }: TicketSearchBarProps) {
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<string>("");

  /** Normalise inputs and forward them to the caller. */
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSearch(keyword.trim(), status === "" ? undefined : status);
  }

  return (
    <form
      onSubmit={handleSubmit}
      style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}
    >
      <label htmlFor="ticket-search-keyword" style={{ display: "none" }}>
        Search tickets
      </label>
      <input
        id="ticket-search-keyword"
        type="text"
        value={keyword}
        placeholder="Search by keyword"
        onChange={(e) => setKeyword(e.target.value)}
      />

      <label htmlFor="ticket-search-status" style={{ display: "none" }}>
        Filter by status
      </label>
      <select
        id="ticket-search-status"
        value={status}
        onChange={(e) => setStatus(e.target.value as TicketStatus | "")}
      >
        {STATUS_OPTIONS.map((option) => (
          <option key={option.value || "ALL"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      <button type="submit">Search</button>
    </form>
  );
}
