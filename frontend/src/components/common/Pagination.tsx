"use client";

/** Props for {@link Pagination}. */
export interface PaginationProps {
  /** Current page index (zero-based, matching the backend `PagedResponse.page`). */
  page: number;
  /** Total number of pages available. */
  totalPages: number;
  /** Callback invoked with the requested zero-based page index. */
  onPageChange: (page: number) => void;
}

/**
 * Previous/Next pagination control with a page indicator.
 *
 * The backend uses zero-based page indices, so the visible indicator displays
 * `page + 1` for a human-friendly "Page X of Y". The Previous button is disabled on
 * the first page and the Next button is disabled on the last page (or when there are
 * no pages), preventing out-of-range requests.
 */
export default function Pagination({
  page,
  totalPages,
  onPageChange,
}: PaginationProps) {
  const isFirstPage = page <= 0;
  const isLastPage = page >= totalPages - 1;
  // Clamp the displayed total so an empty result set still reads sensibly.
  const displayTotal = Math.max(totalPages, 1);

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "0.75rem",
      }}
    >
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={isFirstPage}
        aria-label="Previous page"
      >
        Prev
      </button>
      <span aria-live="polite">
        Page {page + 1} of {displayTotal}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={isLastPage}
        aria-label="Next page"
      >
        Next
      </button>
    </div>
  );
}
