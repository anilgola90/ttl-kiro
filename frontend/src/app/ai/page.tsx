import AiAskPanel from "@/components/ai/AiAskPanel";

/**
 * AI Ask page.
 *
 * Hosts the {@link AiAskPanel} under a page heading, giving users a dedicated place to
 * ask natural-language questions answered strictly from stored ticket data.
 */
export default function AIAskPage() {
  return (
    <main style={{ maxWidth: "720px", margin: "0 auto", padding: "1.5rem" }}>
      <h1>Ask AI</h1>
      <AiAskPanel />
    </main>
  );
}
