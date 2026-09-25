/**
 * Typed API client for the AI ask endpoint (`/api/v1/ai/ask`).
 */

import { apiFetch } from "@/lib/api/client";
import type { AskRequest, AskResponse } from "@/lib/types";

/**
 * Ask a natural-language question grounded strictly in ticket data.
 *
 * @param req - The question payload.
 * @returns The {@link AskResponse} containing the answer, cited sources, and grounding flag.
 */
export function ask(req: AskRequest): Promise<AskResponse> {
  return apiFetch<AskResponse>("/ai/ask", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
