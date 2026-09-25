---
inclusion: manual
---

# Token Optimization Strategy

This document records how token usage is kept low for AI-assisted development on this project,
covering three levers: **context-optimizing MCP servers**, **prompt caching of the static system
prompt**, and **project conventions that keep context small**.

> **Honesty note:** The named third-party MCP servers (Graphify, Caveman, Codebase-memory) are
> configured in [`.kiro/settings/mcp.json`](../settings/mcp.json) but shipped **disabled** by
> default. They require the corresponding CLI/package to be installed on the developer's machine.
> This doc is the reproducible strategy; enable the servers once their packages are available.

---

## 1. Context-optimizing MCP servers

Configured in `.kiro/settings/mcp.json`. Each reduces how much raw source the assistant must load
into context on any given turn:

| Server | What it does | Token benefit |
|---|---|---|
| **Codebase-memory** | Persists file summaries + symbol maps between sessions | Retrieve a compact summary instead of re-reading whole files each turn |
| **Graphify** | Builds a dependency / symbol graph of the codebase | Answer "how do these relate?" from a small graph query rather than loading many files |
| **Caveman** | On-demand context compaction; returns minimal relevant file slices | Load a few hundred tokens of the right lines instead of an entire large file |

### How to enable

1. Install the package (example): `uvx codebase-memory-mcp@latest`
2. In `.kiro/settings/mcp.json`, set the server's `"disabled": false`.
3. Reconnect MCP servers from the Kiro MCP panel (no IDE restart required).
4. Prefer the server's retrieval tools (e.g. `memory_search`, `graph_query`) over full-file reads
   when exploring unfamiliar areas of the codebase.

---

## 2. Prompt caching of the static system prompt

The largest, most-repeated portion of any assistant request is the **static system prompt** — the
steering files, guardrail text, and standing instructions that do not change between questions.
Re-sending them uncached means paying for the same tokens on every turn.

**Strategy:**
- Keep the **stable** instruction blocks (steering files, guardrails, coding standards) at the
  *front* of the context so they form a cache-friendly, unchanging prefix.
- Keep the **volatile** content (the current question, recent tool output, working file) at the
  *end*, after the cached prefix.
- Where the model provider supports explicit prompt caching (e.g. Anthropic `cache_control`
  breakpoints, OpenAI automatic prefix caching), mark the boundary between the static steering
  prefix and the volatile suffix so repeated questions re-use the cached prefix and only pay for
  the changed tail.
- Net effect: repeated questions in a session re-pay only for the small changing suffix, not for
  the full steering/guardrail preamble.

**In this project's own RAG endpoint**, the same principle is applied: the grounding system prompt
in `RagServiceImpl` is a fixed constant (`SYSTEM_PROMPT_TEMPLATE`) with only the retrieved context
and user question substituted in — a naturally cache-friendly shape (stable prefix, variable tail).

---

## 3. Project conventions that keep context small

These are enforced via the other steering files and reduce token pressure regardless of tooling:

- **Small, focused files** — one class per concern; the pure `TicketStateMachine`, per-DTO records,
  and per-service classes mean the assistant loads only what it needs.
- **Interfaces + impls** — depending on the narrow `TicketService` interface lets the assistant
  reason about contracts without loading full implementations.
- **Spec-first** — `requirements.md` / `design.md` / `tasks.md` act as compact, authoritative
  summaries, so the assistant consults the spec instead of re-deriving intent from scattered code.
- **Structured logs** — `docs/prompt-history.md` and `docs/ai-mistakes.md` capture decisions so
  earlier reasoning need not be reconstructed from the full transcript.
- **Targeted retrieval** — use grep/symbol search for specific lookups rather than reading whole
  directories.

---

## Summary

| Lever | Mechanism | Status |
|---|---|---|
| MCP servers | `.kiro/settings/mcp.json` (Codebase-memory, Graphify, Caveman) | Configured, disabled by default until packages installed |
| Prompt caching | Static steering prefix + volatile suffix; provider cache breakpoints | Strategy documented; applied to the RAG system prompt shape |
| Conventions | Small files, interfaces, spec-first, structured logs, targeted search | Enforced across the codebase |
