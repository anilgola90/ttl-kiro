# Prompt History

Record of every prompt given to the AI assistant during development of the Support Ticket Management System.

---

## [2026-09-24 00:00] — Project Setup: Create steering files

**Prompt:**
> ATL / TL Assignment — Build an AI-Powered Support Ticket Management System. First of all create my agents (steering files, skills, commands).

**Summary of AI response:** Created all steering files covering Java/Spring Boot guidelines, testing, API standards, RAG/vector store guidelines, documentation standards, and command files for review-code, review-spec, generate-tests, and review-rag-output. Also created record-prompt skill and docs scaffold.

**Accepted / Modified / Rejected:** Accepted

---

## [2026-09-24 22:30] — Execute all implementation tasks (orchestrated)

**Prompt:**
> start the tasks one by one and if parallel allowed, please do it / continue

**Summary of AI response:** Orchestrated all 38 queued implementation tasks plus the 10 optional
property-based tests through spec-task-execution subagents in dependency-ordered parallel waves:
Gradle/config → domain/Flyway/repos → DTOs/events/exceptions → state machine → services →
embedding + RAG → controllers → unit/slice/property tests → Testcontainers integration tests →
Next.js frontend → docs. Ran the full Gradle test suite and fixed four real defects the AI had
introduced (see docs/ai-mistakes.md): Spring Boot 4 / Spring AI 1.0.0 incompatibility (downgraded
to Boot 3.4.1), @EventListener on an interface-backed proxy, a bad Mockito verification, and a
duplicate @EnableJpaAuditing. Final: 50/52 tests pass; the only 2 failures are Testcontainers
integration tests that require a running Docker daemon.

**Accepted / Modified / Rejected:** Modified — accepted the generated code but corrected the four
defects above and converted the integration tests from the removed TestRestTemplate to MockMvc.

---

## [2026-09-24 23:12] — Live end-to-end verification (Docker + Ollama)

**Prompt:**
> i have run the docker desktop / run the ollama too

**Summary of AI response:** Started a pgvector container, pulled the Ollama models
(nomic-embed-text, llama3.2), and ran the full test suite — 61 tests, 0 failures, including
both Testcontainers integration tests. Booted the app on the dev profile against real
PostgreSQL + Ollama and exercised the live stack: created tickets (201), confirmed async
embeddings landed in the vector store, and verified the RAG /api/v1/ai/ask endpoint returns
grounded, ticket-cited answers for in-scope questions and an honest no-match for out-of-scope
ones. Caught and fixed several real runtime defects along the way (see docs/ai-mistakes.md):
dev-profile dual AI-provider bean ambiguity, hardcoded dev datasource URL shadowing DB_URL,
PGVector dimension mismatch, a misleading `grounded:true` citation on weak retrieval matches
(added a post-generation guardrail), the nomic-embed-text similarity-threshold miscalibration,
and a Testcontainers cross-class container-lifecycle bug (switched to the singleton container
pattern).

**Accepted / Modified / Rejected:** Modified — fixed the runtime/config/RAG defects above.

---
