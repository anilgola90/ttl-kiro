/** Props for {@link LoadingSpinner}. */
export interface LoadingSpinnerProps {
  /** Accessible label describing what is loading. Defaults to `"Loading"`. */
  label?: string;
}

/**
 * A simple, accessible loading indicator.
 *
 * Renders a spinning circle alongside an accessible label. The container is marked
 * with `aria-busy` and `aria-label` so screen readers announce the loading state
 * without depending on the visual animation.
 */
export default function LoadingSpinner({
  label = "Loading",
}: LoadingSpinnerProps) {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-label={label}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: "0.5rem",
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: "1rem",
          height: "1rem",
          border: "2px solid #ccc",
          borderTopColor: "#333",
          borderRadius: "50%",
          display: "inline-block",
          animation: "spin 0.6s linear infinite",
        }}
      />
      <span>{label}</span>
      <style>{"@keyframes spin { to { transform: rotate(360deg); } }"}</style>
    </div>
  );
}
