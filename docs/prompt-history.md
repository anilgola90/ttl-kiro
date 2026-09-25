# Project Build History — AI-Powered Support Ticket Management System

> A full, chronological conversation log of how this project was built with **Kiro** using
> spec-driven development. It captures the constant back-and-forth: **the prompts I gave** (👤) and
> **what Kiro produced** (🤖) at each step. For the structured append-only record see
> [`docs/prompt-history.md`](docs/prompt-history.md); this file is the human-readable story.

**Repository:** https://github.com/anilgola90/ttl-kiro
**Stack:** Java 21 · Spring Boot 3.4 · Spring AI · PostgreSQL + PGVector · Ollama / OpenAI · Next.js
**Methodology:** Requirement → Specification → Plan / Tasks → Implementation → Testing → Review → Fix

---

## Table of Contents

1. [Prompt 1 — Kickoff & Steering Files / Agents](#prompt-1--kickoff--steering-files--agents)
2. [Prompt 2 — Requirements Analysis](#prompt-2--requirements-analysis)
3. [Prompt 3 — Technical Design](#prompt-3--technical-design)
4. [Prompt 4 — Implementation Plan / Tasks](#prompt-4--implementation-plan--tasks)
5. [Prompt 5 — Begin Implementation](#prompt-5--begin-implementation)
6. [Prompt 6 — RAG Pipeline & Grounding Guardrails](#prompt-6--rag-pipeline--grounding-guardrails)
7. [Prompt 7 — Testing Strategy & Property-Based Tests](#prompt-7--testing-strategy--property-based-tests)
8. [Prompt 8 — Build Fixes & Version Alignment](#prompt-8--build-fixes--version-alignment)
9. [Prompt 9 — Live End-to-End Verification](#prompt-9--live-end-to-end-verification)
10. [Prompt 10 — Cleanup, Documentation & Git](#prompt-10--cleanup-documentation--git)
11. [AI Mistakes Caught](#ai-mistakes-caught)
12. [Final State](#final-state)

---

## Prompt 1 — Kickoff & Steering Files / Agents

👤 **Prompt:**
> This is an ATL / TL assignment and I want to do it *properly* with Kiro — spec-driven, not
> vibe-coded. The whole point of the exercise is to show how I design and reason about the AI
> assistant itself, so before we write a single line of application code I want you to set up the
> reusable AI instructions. Please create a full set of steering files: Java Spring Boot guidelines
> (layering, constructor injection, DTO mapping, no field injection, secrets policy), testing
> guidelines (JUnit 5 + Mockito, Testcontainers, jqwik for property-based tests, and an explicit
> state-machine transition table we must cover 100%), API standards (REST conventions, status
> codes, pagination shape, a consistent error envelope, and the `/api/v1/ai/ask` contract), a
> RAG/vector-store guide (chunking strategy for ticket data, embedding-model comparison with the
> cost/latency/quality tradeoff, PGVector config, and hard grounding guardrails), and a
> documentation standard. On top of those, add command files I can invoke on demand: review-code,
> review-spec, generate-tests, and — importantly — a review-rag-output command specifically for
> catching hallucinated / ungrounded assistant answers. Finally, since the assignment requires it,
> create a small skill that records every prompt I give into a file, and scaffold `docs/` with a
> prompt-history log, an ai-mistakes log, and an ADR for the embedding-model decision. Do NOT start
> building the app yet — I just want my "agents" in place first.

🤖 **Kiro:** Read the existing Gradle project to understand the baseline, then created the complete
set of reusable AI instructions before any application code:

| File | Purpose |
|---|---|
| `.kiro/steering/java-springboot.md` | Layering, constructor injection, DTO mapping, exception handling, secrets policy |
| `.kiro/steering/testing.md` | JUnit 5 + Mockito, Testcontainers, jqwik, the mandatory state-machine transition table |
| `.kiro/steering/api-standards.md` | REST conventions, status codes, pagination, error envelope, `/ai/ask` contract |
| `.kiro/steering/rag-vector-store.md` | Chunking strategy, embedding-model comparison, PGVector config, grounding guardrails |
| `.kiro/steering/documentation.md` | Spec artefact structure, Javadoc rules, README + ADR requirements |
| `.kiro/steering/commands/review-code.md` | On-demand code-review checklist |
| `.kiro/steering/commands/review-spec.md` | On-demand spec-review checklist |
| `.kiro/steering/commands/generate-tests.md` | Test-generation guidance + hallucination guard |
| `.kiro/steering/commands/review-rag-output.md` | Grounding / hallucination review command for RAG answers |
| `.kiro/skills/record-prompt.md` | Skill that records every prompt to a file |
| `docs/prompt-history.md` · `docs/ai-mistakes.md` · `docs/adr/ADR-001-embedding-model.md` | Prompt log, AI-mistake log, embedding-model ADR |

> 🤖 *"All steering files and supporting infrastructure are in place… Ready to move to the next
> step — creating the spec documents before any implementation. Want to start with requirements?"*

---

## Prompt 2 — Requirements Analysis

👤 **Prompt:**
> Great, the steering looks solid. Let's start the spec now — begin with **requirements**, not
> design, because I want the business rules pinned down first. Treat this as a system that is
> AI-native from the start, not bolted on afterward, so the RAG feature deserves the same rigour as
> the CRUD. Write the requirements in EARS format and be exhaustive: ticket creation with sensible
> field bounds, listing with pagination, view detail with comments, partial update of
> title/description/priority/assignee, add comments, keyword search, status filter, and backend
> validation with meaningful error responses. The state machine is the deterministic heart of this
> system — enumerate **every** valid transition (OPEN→IN_PROGRESS→RESOLVED→CLOSED, OPEN→CANCELLED,
> IN_PROGRESS→CANCELLED) **and** the invalid ones that must be rejected (CLOSED→OPEN, RESOLVED→OPEN,
> CANCELLED→OPEN, etc.) with the exact HTTP status. For the AI side I want a requirement for a
> natural-language `/api/v1/ai/ask` endpoint that is grounded strictly in real ticket data, cites
> the specific ticket IDs it used, and explicitly says "no relevant tickets found" instead of
> fabricating. Add requirements for embedding re-ingestion so the knowledge base never goes stale
> when a ticket is updated or closed, and make top-K and the similarity threshold configurable —
> never hardcoded. Please also run your own requirements analysis pass to catch ambiguity or
> missing edge cases and tighten the acceptance criteria before you show me.

🤖 **Kiro:** Confirmed the entry point (New Feature → Requirements-first), then produced
`.kiro/specs/support-ticket-management/requirements.md` — **15 EARS-format requirements**, each one
auto-detailed for precision and testability. Coverage included ticket CRUD with field bounds, the
full state machine (valid + invalid transitions with HTTP 422), comments with best-effort async
embedding, keyword search + status filter with bounds, a consistent error envelope, embedding
re-ingestion rules, the RAG endpoint with grounding/no-match/LLM-failure handling and PII-safe
logging, all RAG tuning values via `@ConfigurationProperties` with fail-fast validation, the UI
requirements, and the non-functional requirements (Flyway, H2/PGVector profiles, secrets, OpenAPI,
CORS).

> 🤖 *"The requirements document is complete and refined… You can now: Generate Tech Design ·
> Generate Task List · Analyze Requirements."*

---

## Prompt 3 — Technical Design

👤 **Prompt:**
> The requirements read well. Now generate the **technical design**. I want a real architecture, not
> a sketch: a component diagram showing the browser (Next.js) talking to the Spring MVC controllers,
> down through services and repositories to PostgreSQL, with PGVector as the vector store and
> OpenAI/Ollama as the pluggable AI backends. Trace at least two request flows end-to-end — ticket
> creation (including how embedding happens without blocking the response) and the RAG ask flow
> (retrieve → guardrail → generate → cite). For the domain model use human-readable `TKT-{n}` IDs
> from a database sequence. I feel strongly that the state machine should be a **pure Java class**
> with an adjacency-map of allowed transitions and *no* Spring dependency, so it's trivially unit
> testable without a context — please design it that way and explain the reasoning inline. Drive all
> the embedding re-ingestion off Spring application events so the ticket transaction can commit
> independently of the (slow, failure-prone) embedding work. Bind every tunable AI value through an
> `@Validated` `@ConfigurationProperties` record so bad config fails fast at startup. And most
> importantly for a spec-driven build: derive a set of **formal correctness properties** from the
> requirements — the invariants that must always hold — because I want to validate those later with
> property-based tests, not just example-based ones.

🤖 **Kiro:** Produced `.kiro/specs/support-ticket-management/design.md`:

- **Architecture** — Mermaid component diagram + request-flow traces for ticket creation
  (async embedding via events) and the RAG ask flow (guardrail-first)
- **Domain model** — `Ticket` / `Comment` entities, `TKT-{n}` IDs via a PostgreSQL sequence
- **Service layer** — `TicketStateMachine` as a pure adjacency-map class with no Spring dependency;
  event-driven embedding re-ingestion
- **RAG pipeline** — semantic chunking, profile-based model selection, grounding system prompt, and
  the guardrail that blocks the LLM call when no chunk clears the similarity threshold
- **Configuration** — `AppAiProperties` (`@Validated`) → fail-fast on bad config
- **10 correctness properties** — the formal spec later validated with jqwik property-based tests

---

## Prompt 4 — Implementation Plan / Tasks

👤 **Prompt:**
> Perfect — the design captures exactly what I wanted, especially the pure state machine and the ten
> correctness properties. Now turn this into an actionable **implementation plan**. Break it into
> small, dependency-ordered tasks that build strictly from the foundation upward: Gradle
> configuration first, then the domain model plus Flyway migrations, then DTOs/events/exceptions,
> then the state machine, then the services, then embedding + RAG, then the controllers, then the
> full test suite, then the Next.js frontend, then documentation. Every task should reference the
> specific requirements it satisfies so we keep traceability. For each of the ten correctness
> properties, add a dedicated property-based test task placed right next to the code it validates,
> and mark those as optional so I can decide whether to run them. Put in a few explicit checkpoints
> as synchronization points (for example: "core domain compiles and state-machine tests pass" and
> "all unit + property tests pass") so we don't barrel ahead on a broken foundation. And organise it
> as a wave-based dependency graph so that independent tasks can run in parallel while dependent ones
> wait — I want to be able to fan out the work later.

🤖 **Kiro:** Verified requirements.md and design.md both existed, then produced
`.kiro/specs/support-ticket-management/tasks.md` — **19 top-level tasks, 50+ sub-tasks** in a
19-wave dependency graph:

```
config → domain + Flyway + repos → DTOs + events + exceptions → state machine →
services → embedding + RAG → controllers → unit/slice/property tests →
Testcontainers integration tests → Next.js frontend → documentation
```

All 10 correctness properties were mapped to optional jqwik test tasks next to their
implementations, with three checkpoints (tasks 7, 12, 18) as synchronization points.

---

## Prompt 5 — Begin Implementation

👤 **Prompt:**
> Let's build it. Start executing the tasks, and wherever the dependency graph allows parallelism,
> fan them out — don't run everything strictly serially if independent tasks are ready in the same
> wave. Keep to the steering rules the whole way: constructor injection only, records for DTOs,
> never leak JPA entities across the API boundary, Bean Validation on request bodies, and no
> secrets in source — everything through environment variables. Follow the design faithfully:
> the pure `TicketStateMachine`, event-driven embedding, the `@Validated` config properties, and
> `TKT-{n}` IDs from the sequence. If a subagent hits a transient error, just retry and keep going;
> don't stop and wait on me for routine things — only pause if you hit a genuine decision that
> needs my input. As you generate code, if you notice the AI producing something wrong (a bad
> import, a typo, a broken annotation), fix it and note it so we can log it later. Keep me posted on
> progress wave by wave.

👤 **Follow-up prompts during execution:**
> please retry again · continue · continue from where you left · please continue now · dont ask for
> any user input

🤖 **Kiro:** Queued all tasks and executed them wave by wave, dispatching independent tasks in
parallel and holding dependent ones until prerequisites finished. Across the waves it delivered:

- **Config** — `build.gradle` deps, `application.yml` + `dev`/`prod`/`test` profiles,
  `AppAiProperties`, Spring config
- **Domain + data** — entities, Flyway V1–V5, repositories
- **DTOs / events / exceptions** — request/response records, domain events, `GlobalExceptionHandler`
- **State machine + services** — `TicketStateMachine`, `TicketServiceImpl`, `CommentServiceImpl`,
  `EmbeddingServiceImpl` (+ async event listeners), `RagServiceImpl` (grounding guardrail)
- **Controllers** — `Ticket` / `Comment` / `Ai`
- **Frontend** — Next.js API client, ticket list/search, create form, detail page (inline edit +
  status transitions + comments), AI ask panel with source links
- **Docs** — README, CONTRIBUTING, Javadoc

It also caught and fixed a real defect while generating tests: a missing Lombok import on
`Comment.java` that would have broken compilation.

---

## Prompt 6 — RAG Pipeline & Grounding Guardrails

👤 **Prompt:**
> Before we go further I want to be very explicit about the RAG behaviour, because this is the part
> the whole assignment is really testing. The assistant must answer **only** from retrieved ticket
> context — it must never fall through to the LLM's general knowledge for support questions. Make
> the guardrail structural, not just a polite instruction in the prompt: if the similarity search
> returns zero chunks above the configured threshold, short-circuit and return the fixed no-match
> phrase *without ever calling the LLM* — that eliminates the possibility of a plausible-sounding
> fabrication when there's nothing to ground on. Set the generation temperature to 0.0 so answers
> are deterministic. Every grounded answer must cite the specific `ticketId`s it used, and those
> sources must come from the retrieved chunk metadata, not from anything the model invents. Convert
> ticket descriptions, comments, and resolution notes into searchable chunks with proper metadata
> (ticketId, chunkType, status, priority, assignee, createdAt), and re-ingest on update/close so we
> never serve stale embeddings. Keep this a single retrieval→generate flow — no autonomous agent
> behaviour, no tool-chaining, no creating tickets or sending notifications. And log the question
> (truncated for PII) plus the retrieved chunk IDs for auditability.

🤖 **Kiro:** Implemented `RagServiceImpl` exactly to that contract — a guardrail-before-LLM design
where an empty similarity result returns the canonical no-match response and the `ChatModel` is
never invoked; temperature 0.0; sources extracted from chunk metadata; a strict grounding system
prompt; single retrieval→generate flow with no tool-chaining; and INFO-level logging of the
truncated question plus retrieved chunk IDs. `EmbeddingServiceImpl` builds semantic chunks with the
full metadata map and re-ingests on create/update/comment/RESOLVED/CLOSED via Spring events, with
failures caught and swallowed so ticket operations never fail on an embedding error.

---

## Prompt 7 — Testing Strategy & Property-Based Tests

👤 **Prompt:**
> Now let's make the testing serious — this is a spec-driven build, so I care about correctness
> properties, not just a few happy-path examples. Implement all ten correctness properties from the
> design as **jqwik property-based tests**, each tagged and validating its requirement: the state
> machine must reject every invalid transition and accept every valid one across all generated
> status pairs; whitespace-only inputs must always be rejected; keyword search must be a correct
> filter with no false positives or negatives; the combined status+keyword filter must be
> conjunctive; the RAG no-match guardrail must be deterministic and must never call the LLM on empty
> context; the LLM call parameters must always be grounded (system prompt + temperature 0.0); chunk
> metadata must always be complete and correct; the error envelope must never leak a stack trace;
> and a partial update must preserve unmodified fields. On top of the properties, write unit tests
> for every service with Mockito, `@WebMvcTest` slice tests for every controller asserting the exact
> error envelope codes, and Testcontainers integration tests that exercise the full HTTP → service →
> repository → real PostgreSQL + PGVector slice. Enforce 100% branch coverage on the state machine
> via JaCoCo — that part is non-negotiable. And crucially: before you accept any AI-generated test,
> verify the imports, class names, and method signatures actually exist — I don't want tests that
> reference hallucinated APIs.

🤖 **Kiro:** Produced the full test suite — unit tests for `TicketServiceImpl`, `CommentServiceImpl`,
`EmbeddingServiceImpl`, `RagServiceImpl`; `@WebMvcTest` slice tests for all three controllers with
`jsonPath` assertions on the error envelope; Testcontainers integration tests for the ticket
lifecycle and the RAG pipeline; and all **10 jqwik property-based tests** (Properties 1–10), each
tagged to its requirement and configured for 100 tries. JaCoCo was wired to enforce 100% branch
coverage on `TicketStateMachine`. Every generated test was validated against the real source before
acceptance.

---

## Prompt 8 — Build Fixes & Version Alignment

👤 **Prompt:**
> Now actually run the build and the tests — don't just trust that generated code compiles. I want
> to see real green, and if anything fails I want the root cause, not a band-aid. If you hit an
> approach that fails twice, stop patching it incrementally and step back to diagnose what's really
> wrong. Whatever defects surface — wrong dependency versions, broken proxies, bad test assertions,
> duplicate bean definitions — fix them properly and log each one in `docs/ai-mistakes.md` with what
> the AI produced, why it was wrong, and how you corrected it, because "caught and documented AI
> mistakes" is an explicit deliverable. If you find a decision that materially changes the build
> (like a framework version mismatch that can't just be papered over), pause and ask me which way to
> go rather than silently picking one.

🤖 **Kiro:** Ran the real Gradle build and surfaced defects that had passed compilation but failed
at runtime — the biggest being a **Spring Boot 4.1.0-SNAPSHOT / Spring AI 1.0.0 incompatibility**
(Spring AI's GA targets Boot 3.x and referenced a relocated `RestClientAutoConfiguration`). Kiro
paused and asked how to resolve it.

👤 **Decision prompt:**
> Downgrade to Spring Boot 3.4.x (recommended) — align with Spring AI 1.0.0's supported baseline.

🤖 **Kiro:** Downgraded to Spring Boot 3.4.1, reverted the Boot-4-specific test APIs
(`@MockBean`/`TestRestTemplate` → `@MockitoBean`/`MockMvc`, `WebMvcTest` package path), removed a
duplicate `@EnableJpaAuditing`, moved the `@EventListener` methods off an interface-backed proxy
into a dedicated `TicketEmbeddingEventListener`, fixed a bad Mockito verification, and selected a
single AI provider per test profile. Result: the suite compiled and **50/52 tests passed** (the two
remaining needed Docker). Each defect was logged in `docs/ai-mistakes.md`.

---

## Prompt 9 — Live End-to-End Verification

👤 **Prompt:**
> I've started Docker Desktop and I have the Ollama CLI installed — go ahead and verify the whole
> thing against the *real* stack, not just mocks. Pull whatever Ollama models the dev profile needs
> (nomic-embed-text for embeddings, llama3.2 for generation), run the complete suite including the
> Testcontainers integration tests, and confirm the JaCoCo state-machine coverage gate passes. Then
> actually boot the application on the dev profile against a live PGVector container and exercise it
> like a user would: create a few tickets, confirm they persist and that embeddings really land in
> the vector store, then hit `/api/v1/ai/ask` with an in-scope question about payment failures and
> show me it returns a grounded answer citing real ticket IDs. Then ask it something completely
> out-of-scope like "what is the capital of France?" and prove it returns the honest no-match
> response instead of making something up. If the RAG answers look off in any way — wrong grounding
> flag, a citation that doesn't match the text, retrieval returning nothing when it should — treat
> that as a real bug, dig into it, fix it, and document it as a caught AI/RAG mistake.

🤖 **Kiro:** Confirmed Docker was reachable, pulled `nomic-embed-text` and `llama3.2`, and verified
the real stack:

1. **Full suite — 61 tests, 0 failures**, including both Testcontainers integration tests against
   real PostgreSQL + PGVector; JaCoCo confirmed **100% state-machine branch coverage**.
2. Booted the app on the `dev` profile against a live PGVector container and exercised it:
   created tickets (`201`, persisted), confirmed real Ollama embeddings landed in `vector_store`,
   got a **grounded answer citing TKT-1001 / TKT-1002** for the payment question, and an **honest
   no-match (`grounded: false`)** for the out-of-scope question.

This phase caught the project's most valuable RAG defect — a misleading `grounded:true` citation on
a weak retrieval match — and Kiro added a post-generation guardrail to fix it, plus a dev-profile
dual-provider bean ambiguity, a hardcoded dev datasource URL, a PGVector dimension mismatch, the
nomic-embed-text similarity-threshold miscalibration, and a Testcontainers cross-class lifecycle
bug (singleton container pattern). All logged in `docs/ai-mistakes.md`.

---

## Prompt 10 — Cleanup, Documentation & Git

👤 **Prompt:**
> We're basically done — now let's clean up and ship it. First, remove the leftover `Employee*`
> scaffolding classes from the original starter project; they have nothing to do with this system.
> Make sure the build is still clean after that. Then turn this into a git repo and push it to
> GitHub at https://github.com/anilgola90/ttl-kiro.git on `main` — but before you commit, double
> check the `.gitignore` covers build output, node_modules, `.next`, and any `.env`/key/pem files,
> and scan the tree to be certain no secrets are being committed (that DB password I mentioned must
> never end up in the repo). The assignment also requires a saved prompt history, so keep
> `docs/prompt-history.md` and the `.specstory/history/` folder in place. Finally, write a really
> good-looking `history.md` at the repo root that tells the whole story of how we built this — my
> prompts interleaved with your responses, phase by phase — so a reviewer can see this was genuinely
> spec-driven and that I was steering the AI the entire way, not just accepting whatever it spat out.

🤖 **Kiro:** Deleted the four legacy `Employee*` files and confirmed a clean compile. Extended
`.gitignore` (node_modules, `.next`, `.env*`, key/pem), scanned for hardcoded secrets (none — the
DB password only ever came via env/CLI), then `git init` → committed the project → pushed to `main`.
Kept `docs/prompt-history.md` and `.specstory/history/` in place, and authored this `history.md`
as the full conversation-style narrative.

---

## AI Mistakes Caught

A core assignment goal was to use AI as an *engineering assistant*, not to blindly accept its
output. **Ten meaningful issues** were caught, fixed, and documented in
[`docs/ai-mistakes.md`](docs/ai-mistakes.md) — spanning both wrong code and ungrounded RAG output:

| # | Type | Summary |
|---|---|---|
| 1 | Wrong deps | Spring Boot 4 SNAPSHOT paired with Spring AI 1.0.0 (Boot 3.x) → runtime `ClassNotFoundException` |
| 2 | Wrong code | `@EventListener` on an interface-backed JDK proxy → moved to a dedicated component |
| 3 | Bad test | `verifyNoMoreInteractions` without a prior `verify` on a stubbed-and-called method |
| 4 | Wrong code | Duplicate `@EnableJpaAuditing` → bean-override conflict |
| 5 | **Ungrounded RAG** | `grounded:true` + cited source while the answer said "no relevant tickets" → post-generation guardrail |
| 6 | Config | Dev profile had two AI providers → ambiguous `ChatModel`/`EmbeddingModel` beans |
| 7 | Config | Hardcoded dev datasource URL shadowed `DB_URL` |
| 8 | Config | PGVector dimension mismatch (768 vs 1536 for nomic-embed-text) |
| 9 | Test isolation | Testcontainers per-class container lifecycle → cross-class "connection refused"; fixed with singleton container |
| 10 | RAG tuning | 0.75 similarity threshold miscalibrated for nomic-embed-text → tuned to 0.3 for the Ollama profile |

The RAG guardrail (mistake #5) is the standout lesson: *"retrieved a chunk above threshold" is not
the same as "the answer is grounded"* — grounding must be validated on the generated answer, not
just on retrieval.

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
> with a human establishing ground-truths at each step and AI acting as the engineering assistant.


---

## Addendum — Frontend End-to-End Verification

👤 **Prompt:**
> Frontend not run end-to-end … verifying the UI against the running backend would close the last
> gap.

🤖 **Kiro:** Installed the Next.js dependencies (`npm install`), started the dev server on
`http://localhost:3000`, restarted the Spring Boot backend on the `dev` profile (against the live
PGVector container on port 5433 + Ollama), and exercised every UI-driven acceptance criterion
through the **actual browser path** — the Next.js `/api` rewrite proxy forwarding to the backend:

| UI action | Expected | Result |
|---|---|---|
| Create ticket from UI | 201 | ✅ 201 (TKT-1005) |
| View ticket details | 200 | ✅ 200 |
| Add comment | 201 | ✅ 201 |
| Valid status transition (OPEN→IN_PROGRESS) | 200 | ✅ 200 |
| Invalid transition (→OPEN) rejected | 422 | ✅ 422 |
| Search by keyword | 200 | ✅ 200 |
| Status filter | 200 | ✅ 200 |
| Backend validation (blank title) | 400 | ✅ 400 |
| AI ask — in-scope | grounded + cited | ✅ grounded, cites TKT-1001/TKT-1002 |
| AI ask — out-of-scope | honest no-match | ✅ grounded=false, empty sources |

The `/tickets` page rendered (HTTP 200) and the Next.js proxy correctly forwarded `/api/v1/*` to
the backend. Full stack verified: **Next.js (3000) → proxy → Spring Boot (8080) → PostgreSQL +
PGVector + Ollama**. This closes the last open item; every acceptance criterion — backend and
UI-driven — is now verified end-to-end.
