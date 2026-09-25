/**
 * Shared HTTP client for the support ticket API.
 *
 * All requests go through {@link apiFetch}, a thin wrapper over the native `fetch`
 * API that centralises base-path resolution, JSON handling, and error translation.
 *
 * Requests target the relative base path {@link API_BASE_PATH} (`/api/v1`). In
 * development the Next.js dev server proxies this prefix to the Spring Boot backend
 * via the rewrites configured in `next.config.ts`, so no absolute origin is needed.
 */

import type { ErrorResponse } from "@/lib/types";

/**
 * Relative base path for every backend call. Kept relative so that Next.js
 * rewrites can transparently proxy requests to the backend origin.
 */
export const API_BASE_PATH = "/api/v1";

/** Generic message used when the server response body is missing or not valid JSON. */
const GENERIC_ERROR_MESSAGE = "An unexpected error occurred. Please try again.";

/**
 * Perform a typed JSON request against the backend API.
 *
 * On a successful response the parsed JSON body is returned as `T`. A `204 No Content`
 * response resolves to `undefined` since there is no body to parse.
 *
 * On any non-2xx response the body is parsed as an {@link ErrorResponse} and an `Error`
 * is thrown whose `message` is the server-provided `message`. If the error body cannot
 * be parsed as JSON, a generic fallback message is used instead.
 *
 * @typeParam T - The expected shape of the successful JSON response body.
 * @param path - Path relative to {@link API_BASE_PATH}, beginning with `/` (e.g. `/tickets`).
 * @param options - Standard `fetch` options (method, body, headers, ...). A JSON
 *   `Content-Type` header is applied by default and may be overridden.
 * @returns A promise resolving to the parsed body as `T`, or `undefined` for `204`.
 * @throws {Error} When the response status is not in the 2xx range. The error message
 *   is taken from the {@link ErrorResponse} body when available.
 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_PATH}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new Error(await extractErrorMessage(response));
  }

  // 204 No Content (and other empty bodies) have nothing to parse.
  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * Read an error {@link ErrorResponse} body and return its `message`, falling back
 * to a generic message when the body is absent or not valid JSON.
 *
 * @param response - The failed (`non-2xx`) response.
 * @returns A human-readable error message.
 */
async function extractErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as Partial<ErrorResponse>;
    if (body && typeof body.message === "string" && body.message.length > 0) {
      return body.message;
    }
  } catch {
    // Body was empty or not JSON — fall through to the generic message.
  }
  return GENERIC_ERROR_MESSAGE;
}
