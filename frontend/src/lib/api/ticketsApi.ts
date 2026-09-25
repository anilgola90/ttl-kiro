/**
 * Typed API client for ticket resources (`/api/v1/tickets`).
 *
 * Every function delegates to {@link apiFetch}, so non-2xx responses are surfaced as
 * `Error`s carrying the backend {@link ErrorResponse} message.
 */

import { apiFetch } from "@/lib/api/client";
import type {
  CreateTicketRequest,
  PagedResponse,
  Ticket,
  TicketSummary,
  UpdateStatusRequest,
  UpdateTicketRequest,
} from "@/lib/types";

/** Query parameters accepted by {@link listTickets}. */
export interface ListTicketsParams {
  /** Optional status filter (e.g. `"OPEN"`). */
  status?: string;
  /** Optional keyword search across title and description. */
  search?: string;
  /** Zero-based page index. */
  page?: number;
  /** Page size. */
  size?: number;
}

/**
 * Build a query string from the provided params, omitting any `undefined` values.
 *
 * @param params - The list parameters, any of which may be absent.
 * @returns A query string beginning with `?`, or an empty string when no params are set.
 */
function buildTicketsQuery(params: ListTicketsParams): string {
  const query = new URLSearchParams();
  if (params.status !== undefined) query.set("status", params.status);
  if (params.search !== undefined) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const queryString = query.toString();
  return queryString ? `?${queryString}` : "";
}

/**
 * List tickets, optionally filtered by status and/or keyword and paginated.
 *
 * @param params - Optional status, search, page, and size filters.
 * @returns A paged envelope of {@link TicketSummary} projections.
 */
export function listTickets(
  params: ListTicketsParams = {},
): Promise<PagedResponse<TicketSummary>> {
  return apiFetch<PagedResponse<TicketSummary>>(
    `/tickets${buildTicketsQuery(params)}`,
  );
}

/**
 * Fetch a single ticket by its identifier, including its ordered comments.
 *
 * @param id - The ticket identifier (e.g. `"TKT-1001"`).
 * @returns The full {@link Ticket} detail.
 */
export function getTicket(id: string): Promise<Ticket> {
  return apiFetch<Ticket>(`/tickets/${encodeURIComponent(id)}`);
}

/**
 * Create a new support ticket.
 *
 * @param req - The ticket creation payload.
 * @returns The newly created {@link Ticket}.
 */
export function createTicket(req: CreateTicketRequest): Promise<Ticket> {
  return apiFetch<Ticket>("/tickets", {
    method: "POST",
    body: JSON.stringify(req),
  });
}

/**
 * Partially update a ticket's mutable fields. Only fields present in `req` are changed.
 *
 * @param id - The ticket identifier.
 * @param req - The partial update payload.
 * @returns The updated {@link Ticket}.
 */
export function updateTicket(
  id: string,
  req: UpdateTicketRequest,
): Promise<Ticket> {
  return apiFetch<Ticket>(`/tickets/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(req),
  });
}

/**
 * Transition a ticket to a new lifecycle status.
 *
 * @param id - The ticket identifier.
 * @param req - The target status and optional resolution notes.
 * @returns The updated {@link Ticket}.
 */
export function updateStatus(
  id: string,
  req: UpdateStatusRequest,
): Promise<Ticket> {
  return apiFetch<Ticket>(`/tickets/${encodeURIComponent(id)}/status`, {
    method: "PATCH",
    body: JSON.stringify(req),
  });
}
