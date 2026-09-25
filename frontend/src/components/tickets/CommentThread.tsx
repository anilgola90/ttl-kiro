import type { Comment } from "@/lib/types";

/** Props for {@link CommentThread}. */
export interface CommentThreadProps {
  /** The comments to render. Displayed oldest-first. */
  comments: Comment[];
}

/**
 * Format an ISO-8601 UTC timestamp for display.
 *
 * Falls back to the raw string if it cannot be parsed, so malformed input never
 * throws while rendering.
 *
 * @param iso - An ISO-8601 timestamp string.
 * @returns A locale-formatted date/time, or the original string on parse failure.
 */
function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}

/**
 * Renders a ticket's comment thread, oldest-first.
 *
 * The backend already returns comments ordered oldest-first, but this component sorts
 * defensively by `createdAt` so display order is correct regardless of input order.
 * Each entry shows the author, the comment body, and the creation timestamp. An empty
 * list renders a friendly placeholder.
 */
export default function CommentThread({ comments }: CommentThreadProps) {
  if (comments.length === 0) {
    return <p>No comments yet.</p>;
  }

  const ordered = [...comments].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );

  return (
    <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
      {ordered.map((comment) => (
        <li
          key={comment.id}
          style={{
            padding: "0.75rem 0",
            borderBottom: "1px solid #e0e0e0",
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: "1rem",
              fontSize: "0.85rem",
              color: "#555",
            }}
          >
            <strong>{comment.author}</strong>
            <time dateTime={comment.createdAt}>
              {formatTimestamp(comment.createdAt)}
            </time>
          </div>
          <p style={{ margin: "0.25rem 0 0", whiteSpace: "pre-wrap" }}>
            {comment.body}
          </p>
        </li>
      ))}
    </ul>
  );
}
