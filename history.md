# Project Build History — AI-Powered Support Ticket Management System

> A chronological narrative of how this project was built with Kiro using spec-driven
> development. Each section captures the **user prompt** that drove the work and a **summary of
> what was produced** in response. For the structured, machine-appendable log see
> [`docs/prompt-history.md`](docs/prompt-history.md); this file is the human-readable story.

**Repository:** https://github.com/anilgola90/ttl-kiro
**Stack:** Java 21 · Spring Boot 3.4 · Spring AI · PostgreSQL + PGVector · Ollama/OpenAI · Next.js
**Methodology:** Requirement → Specification → Plan/Tasks → Implementation → Testing → Review → Fix

---

## Table of Contents

1. [Phase 0 — Steering Files & Agents](#phase-0--steering-files--agents)
2. [Phase 1 — Requirements](#phase-1--requirements)
3. [Phase 2 — Design](#phase-2--design)
4. [Phase 3 — Tasks](#phase-3--tasks)
5. [Phase 4 — Implementation (Orchestrated)](#phase-4--implementation-orchestrated)
6. [Phase 5 — Build Fixes & Version Alignment](#phase-5--build-fixes--version-alignment)
7. [Phase 6 — Live End-to-End Verification](#phase-6--live-end-to-end-verification)
8. [Phase 7 — Cleanup & Git](#phase-7--cleanup--git)
9. [AI Mistakes Caught](#ai-mistakes-caught)
10. [Final State](#final-state)

---

## Phase 0 — Steering Files & Agents

> **Prompt:**
> *"ATL / TL Assignment … Build an AI-Powered Support Ticket Management System … first of all create my agents."*

Before writing a single line of application code, the reusable AI instructions (Kiro "steering"
files) were established so every later step inherited a consistent set of standards.

**Produced:**

| File | Purpose |
|---|---|
| `.kiro/steering/java-springboot.md` | Layering rules, constructor injection, DTO mapping, exception handling, secrets policy |
| `.kiro/steering/testing.md` | JUnit 5 + Mockito, Testcontainers, jqwik property tests, the mandatory state-machine transition table |
| `.kiro/steering/api-standards.md` | REST conventions, status codes, pagination, error envelope, the `/ai/ask` contract |
| `.kiro/steering/rag-vector-store.md` | Chunking strategy, embedding-model comparison, PGVector config, grounding guardrails |
| `.kiro/steering/documentation.md` | Spec artefact structure, Javadoc rules, README + ADR requirements |
| `.kiro/steering/commands/review-code.md` | On-demand code-review checklist |
| `.kiro/steering/commands/review-spec.md` | On-demand spec-review checklist |
| `.kiro/steering/commands/generate-tests.md` | Test-generation guidance + hallucination guard |
| `.kiro/steering/commands/review-rag-output.md` | Grounding / hallucination review command for RAG answers |
| `.kiro/skills/record-prompt.md` | Skill instructing the assistant to log every prompt |
| `docs/prompt-history.md` | The running prompt log |
| `docs/ai-mistakes.md` | Log of meaningful AI errors caught during development |
| `docs/adr/ADR-001-embedding-model.md` | Architecture Decision Record for the embedding-model choice |

**Why it matters:** These artefacts demonstrate *reusable AI instructions across the project
context* — the assignment's core learning goal. Every subsequent phase was steered by them.

---

## Phase 1 — Requirements

> **Prompt:**
> *"yes"* (choosing **New Feature → Requirements-first** when Kiro asked how to start)

**Produced:** `.kiro/specs/support-ticket-management/requirements.md` — **15 requirements** written
in EARS format (`WHEN`/`IF-THEN`/`THE … SHALL`), covering:

- Ticket CRUD with explicit field bounds (title 1–200, description 1–5000 chars)
- The full state machine with every valid **and** invalid transition enumerated
- Comments with best-effort async embedding (comment creation never fails on embedding error)
- Keyword search + status filter (with a keyword length bound)
- A consistent error envelope across all failure types
- Embedding re-ingestion rules (create / update / comment / RESOLVED / CLOSED)
- The RAG endpoint — grounding guardrail, honest no-match, LLM-failure handling, PII-safe logging
- All RAG tuning values bound via `@ConfigurationProperties` with fail-fast validation
- UI requirements (list, detail, AI ask panel)
- Non-functional requirements (Flyway, H2/PGVector profiles, secrets, OpenAPI, CORS)

Each requirement was then automatically *detailed* (refined for precision and testability) before
being presented for review.

---

## Phase 2 — Design

> **Prompt:**
> *"Create the design for support-ticket-management"*

**Produced:** `.kiro/specs/support-ticket-management/design.md` — a full technical design:

- **Architecture**: component diagram (React → Spring MVC → services → repositories → PostgreSQL +
  PGVector, with OpenAI/Ollama as AI backends) plus request-flow traces for ticket creation and
  the RAG ask flow
- **Domain model**: `Ticket` / `Comment` entities, `TKT-{n}` IDs via a PostgreSQL sequence
- **Service layer**: `TicketStateMachine` as a *pure Java class* (adjacency-map, no Spring
  dependency — trivially unit-testable), event-driven embedding re-ingestion
- **RAG pipeline**: semantic chunking, profile-based model selection, grounding system prompt,
  and the guardrail that blocks the LLM call when no chunks pass the similarity threshold
- **Configuration**: `AppAiProperties` (`@Validated`) so bad config fails fast at startup
- **10 correctness properties** — the formal specification later validated via property-based tests

---

## Phase 3 — Tasks

> **Prompt:**
> *"Create the tasks for support-ticket-management"*

**Produced:** `.kiro/specs/support-ticket-management/tasks.md` — **19 top-level tasks, 50+ sub-tasks**,
ordered by dependency into a 19-wave graph:

```
config → domain + Flyway + repos → DTOs + events + exceptions → state machine →
services → embedding + RAG → controllers → unit/slice/property tests →
Testcontainers integration tests → Next.js frontend → documentation
```

Each of the 10 correctness properties was mapped to an optional jqwik property-based test task
placed next to its implementation. Three checkpoints (tasks 7, 12, 18) act as synchronization
points.

---

## Phase 4 — Implementation (Orchestrated)

> **Prompt:**
> *"start the tasks one by one and if parallel allowed, please do it"*

The task list was queued and executed wave-by-wave. Independent tasks in the same wave were
dispatched in parallel; dependent tasks waited for their prerequisites.

**Delivered:**

- **Backend** — domain entities, Flyway migrations (V1–V5), repositories, all request/response
  record DTOs, domain events, `GlobalExceptionHandler`, `TicketStateMachine`, `TicketServiceImpl`,
  `CommentServiceImpl`, `EmbeddingServiceImpl` (+ async event listeners), `RagServiceImpl` with the
  grounding guardrail, and all three controllers (`Ticket`, `Comment`, `Ai`)
- **Tests** — unit tests for every service, `@WebMvcTest` slice tests for every controller, and all
  **10 jqwik property-based tests**, plus Testcontainers integration tests for the DB + RAG slices
- **Frontend** — a Next.js app: typed API client, ticket list/search, create form, ticket detail
  (inline edit + status transitions + comments), and the AI ask panel with clickable source links
- **Docs** — README, CONTRIBUTING, ADR, Javadoc across all public types

---

## Phase 5 — Build Fixes & Version Alignment

> **Prompts:**
> *"please retry again"*, *"continue"*, *"continue from where you left"*

Running the real Gradle build surfaced defects that had passed compilation but failed at runtime.
The most significant was a **Spring Boot 4 / Spring AI 1.0.0 incompatibility**: Spring AI's GA
release targets Spring Boot 3.x and its auto-configuration referenced a class removed in Boot 4.

> **Decision prompt:** the user chose **"Downgrade to Spring Boot 3.4.x (recommended)"** when asked
> how to resolve the version mismatch.

Following that decision, the build was aligned to Spring Boot 3.4.1 and the Boot-4-specific test
APIs were reverted accordingly. Several other runtime issues were fixed (see
[AI Mistakes Caught](#ai-mistakes-caught)). Result: the full suite compiled and **50/52 tests
passed** (the 2 remaining needed Docker).

---

## Phase 6 — Live End-to-End Verification

> **Prompts:**
> *"i have run the docker desktop … you can start docker now"* and *"run the ollama too … i have ollama cli installed"*

With Docker and Ollama available, the system was verified against the *real* stack:

1. Pulled the Ollama models `nomic-embed-text` (embeddings) and `llama3.2` (generation)
2. Ran the complete suite — **61 tests, 0 failures**, including both Testcontainers integration
   tests against real PostgreSQL + PGVector; JaCoCo confirmed **100% state-machine branch coverage**
3. Booted the app on the `dev` profile against a live PGVector container and exercised it:
   - Created tickets → `201`, persisted, `TKT-1001`…
   - Confirmed async embeddings landed in the `vector_store` table (real Ollama vectors)
   - `POST /api/v1/ai/ask` "Have we seen payment failures before?" → **grounded answer citing
     TKT-1001 / TKT-1002**, `sources` populated
   - Out-of-scope "What is the capital of France?" → **honest no-match, `grounded: false`** — no
     fabrication

This phase caught the most valuable RAG defect of the project (a misleading `grounded:true`
citation on a weak retrieval match) and the embedding-model similarity-threshold miscalibration —
both documented and fixed.

---

## Phase 7 — Cleanup & Git

> **Prompts:**
> *"remove employee related code which was there earlier"* and
> *"make this a git repo and push code on github …"*

- Deleted the four legacy `Employee*` scaffolding files; confirmed a clean compile afterward
- Extended `.gitignore` (node_modules, `.next`, `.env*`, key/pem files)
- Scanned for hardcoded secrets — none present (the DB password was only ever passed via env/CLI)
- `git init` → committed 129 files → pushed to `https://github.com/anilgola90/ttl-kiro.git` on `main`

---

## AI Mistakes Caught

A core assignment goal was to use AI as an engineering assistant, not blindly accept its output.
**Ten meaningful issues** were caught, fixed, and documented in
[`docs/ai-mistakes.md`](docs/ai-mistakes.md) — spanning both wrong code and ungrounded RAG output:

| # | Type | Summary |
|---|---|---|
| 1 | Wrong deps | Spring Boot 4 SNAPSHOT paired with Spring AI 1.0.0 (Boot 3.x) → runtime `ClassNotFoundException` |
| 2 | Wrong code | `@EventListener` on an interface-backed proxy → moved to a dedicated component |
| 3 | Bad test | `verifyNoMoreInteractions` without a prior `verify` on a stubbed call |
| 4 | Wrong code | Duplicate `@EnableJpaAuditing` → bean-override conflict |
| 5 | **Ungrounded RAG** | `grounded:true` + cited source while the answer said "no relevant tickets" → post-generation guardrail |
| 6 | Config | Dev profile had two AI providers → ambiguous `ChatModel`/`EmbeddingModel` beans |
| 7 | Config | Hardcoded dev datasource URL shadowed `DB_URL` |
| 8 | Config | PGVector dimension mismatch (768 vs 1536 for nomic-embed-text) |
| 9 | Test isolation | Testcontainers per-class container lifecycle → cross-class "connection refused"; fixed with singleton container |
| 10 | RAG tuning | 0.75 similarity threshold miscalibrated for nomic-embed-text → tuned to 0.3 for the Ollama profile |

The RAG guardrail (mistake #5) is the standout: *"retrieved a chunk above threshold" is not the
same as "the answer is grounded"* — grounding must be validated on the generated answer, not just
on retrieval.

---

## Final State

- **Tests:** 61 passing, 0 failures (from a clean build) — unit, `@WebMvcTest` slice, 10 jqwik
  property-based tests, and Testcontainers integration tests
- **Coverage:** 100% branch coverage on `TicketStateMachine` (JaCoCo-enforced)
- **Live-verified:** full CRUD + state machine + grounded RAG against real PostgreSQL + PGVector +
  Ollama
- **Secrets:** none committed; all credentials via environment variables
- **Repo:** pushed to GitHub on `main`

> Requirement → Specification → Plan → Implementation → Testing → Review → Fix — the full loop,
> with AI as the engineering assistant and a human establishing ground-truths at each step.
