import Link from "next/link";

/** Props for {@link SourceLinks}. */
export interface SourceLinksProps {
  /** Ticket identifiers cited as evidence for an answer (e.g. `["TKT-1001"]`). */
  sources: string[];
}

/**
 * Renders the cited ticket sources for a grounded AI answer as clickable chips.
 *
 * Each entry in {@link SourceLinksProps.sources} becomes a `next/link` chip pointing
 * to that ticket's detail page (`/tickets/{id}`), letting the user jump straight to the
 * evidence behind an answer. Renders `null` when there are no sources so callers can
 * pass the `sources` array directly without guarding at the call site (e.g. ungrounded
 * responses supply an empty array).
 */
export default function SourceLinks({ sources }: SourceLinksProps) {
  // Nothing to show when no sources were cited (ungrounded or empty result).
  if (sources.length === 0) {
    return null;
  }

  return (
    <div
      style={{
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: "0.5rem",
        marginTop: "0.75rem",
      }}
    >
      <span style={{ fontWeight: 600 }}>Sources:</span>
      {sources.map((id) => (
        <Link
          key={id}
          href={`/tickets/${id}`}
          style={{
            display: "inline-block",
            padding: "0.15rem 0.6rem",
            border: "1px solid #b6d4fe",
            borderRadius: "999px",
            backgroundColor: "#cfe2ff",
            color: "#084298",
            textDecoration: "none",
            fontSize: "0.85rem",
          }}
        >
          {id}
        </Link>
      ))}
    </div>
  );
}
