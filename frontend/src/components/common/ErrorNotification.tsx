"use client";

/** Props for {@link ErrorNotification}. */
export interface ErrorNotificationProps {
  /** The error message to display. When `null` or empty, nothing is rendered. */
  message: string | null;
  /** Optional callback invoked when the user dismisses the banner. */
  onDismiss?: () => void;
}

/**
 * Displays a prominent, accessible error banner.
 *
 * Renders `null` when there is no message so callers can pass the current error
 * state directly without guarding at the call site. When a message is present it
 * shows a red banner with `role="alert"` so assistive technologies announce it,
 * plus an optional dismiss button when {@link ErrorNotificationProps.onDismiss} is
 * provided.
 */
export default function ErrorNotification({
  message,
  onDismiss,
}: ErrorNotificationProps) {
  // Treat null, empty, and whitespace-only messages as "no error".
  if (message == null || message.trim() === "") {
    return null;
  }

  return (
    <div
      role="alert"
      style={{
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: "0.75rem",
        padding: "0.75rem 1rem",
        border: "1px solid #f5c2c7",
        borderRadius: "6px",
        backgroundColor: "#f8d7da",
        color: "#842029",
      }}
    >
      <span>{message}</span>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss error"
          style={{
            border: "none",
            background: "transparent",
            color: "#842029",
            cursor: "pointer",
            fontSize: "1.1rem",
            lineHeight: 1,
          }}
        >
          &times;
        </button>
      )}
    </div>
  );
}
