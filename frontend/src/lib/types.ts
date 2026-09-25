/**
 * TypeScript mirrors of the backend DTOs.
 *
 * These types intentionally match the JSON shapes produced/consumed by the Spring Boot
 * API (see `com.example.kirotest.dto`). Timestamps are ISO-8601 UTC strings on the wire,
 * so they are typed as `string` here. Enum values are UPPER_SNAKE_CASE strings.
 */

/** Ticket lifecycle status (mirrors backend {@code TicketStatus} enum). */
export type TicketStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "RESOLVED"
  | "CLOSED"
  | "CANCELLED";

/** Business priority (mirrors backend {@code Priority} enum). */
export type Priority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

// ---- Response DTOs ----

/** Compact ticket projection used in list/search results. */
export interface TicketSummary {
  id: string;
  title: string;
  status: TicketStatus;
  priority: Priority;
  /** Current owner, or null if unassigned. */
  assignee: string | null;
  /** ISO-8601 UTC timestamp. */
  createdAt: string;
  /** ISO-8601 UTC timestamp. */
  updatedAt: string;
}

/** A single comment on a ticket. */
export interface Comment {
  /** UUID string. */
  id: string;
  body: string;
  author: string;
  /** ISO-8601 UTC timestamp. */
  createdAt: string;
}

/** Full ticket detail including its ordered comments (oldest-first). */
export interface Ticket {
  id: string;
  title: string;
  description: string;
  status: TicketStatus;
  priority: Priority;
  /** Current owner, or null if unassigned. */
  assignee: string | null;
  /** Notes describing the resolution, or null if unresolved. */
  resolutionNotes: string | null;
  /** ISO-8601 UTC timestamp. */
  createdAt: string;
  /** ISO-8601 UTC timestamp. */
  updatedAt: string;
  comments: Comment[];
}

/** Generic pagination envelope for list endpoints. */
export interface PagedResponse<T> {
  data: T[];
  /** Zero-based current page index. */
  page: number;
  /** Requested page size. */
  size: number;
  /** Total matching items across all pages. */
  totalElements: number;
  /** Total number of pages available. */
  totalPages: number;
}

/** Response for the RAG "ask" endpoint. */
export interface AskResponse {
  /** The generated answer, or the no-match phrase when ungrounded. */
  answer: string;
  /** Ticket identifiers cited as evidence; empty when ungrounded. */
  sources: string[];
  /** Whether the answer is backed by retrieved ticket data. */
  grounded: boolean;
}

/** Standard error envelope returned for all failed requests. */
export interface ErrorResponse {
  /** Machine-readable error code (e.g. "TICKET_NOT_FOUND"). */
  error: string;
  /** Human-readable error message. */
  message: string;
  /** ISO-8601 UTC timestamp. */
  timestamp: string;
  /** Request path that produced the error. */
  path: string;
}

// ---- Request DTOs ----

/** Payload for creating a new support ticket. */
export interface CreateTicketRequest {
  title: string;
  description: string;
  priority: Priority;
  /** Optional ticket owner; omit to leave unassigned. */
  assignee?: string;
}

/**
 * Payload for partially updating a ticket.
 * Every field is optional; omit a field to leave it unchanged.
 */
export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  priority?: Priority;
  assignee?: string;
}

/** Payload for changing a ticket's lifecycle status. */
export interface UpdateStatusRequest {
  status: TicketStatus;
  /** Optional notes explaining the resolution. */
  resolutionNotes?: string;
}

/** Payload for adding a comment to a ticket. */
export interface AddCommentRequest {
  body: string;
  author: string;
}

/** Payload for asking a natural-language question grounded in ticket data. */
export interface AskRequest {
  question: string;
}
