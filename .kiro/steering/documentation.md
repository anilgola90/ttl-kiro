# Documentation Guidelines

## What Must Be Documented
Every spec artefact must exist before implementation begins:

```
spec/
├── requirements.md       — functional + non-functional requirements
├── architecture.md       — system design, component diagram, embedding model choice, chunking strategy
├── data-model.md         — entity definitions, relationships, DB schema
├── api-contract.md       — all REST endpoints, request/response shapes, status codes
├── state-machine.md      — ticket lifecycle, valid/invalid transitions, enforcement point
├── rag-ingestion.md      — chunking strategy, metadata schema, re-ingestion triggers
├── rag-api-contract.md   — /api/ai/ask contract, grounding rules, no-match behaviour
├── evaluation-strategy.md — RAG quality metrics, hallucination detection approach
├── ui-flow.md            — page/screen flows, component responsibilities
└── test-strategy.md      — unit/integration/RAG test plan, coverage targets
```

## Code Documentation
- Every public class and public method must have a Javadoc comment
- Javadoc must describe *what* and *why*, not just repeat the method signature
- Complex business logic (state machine, RAG pipeline) requires inline comments explaining the reasoning
- TODOs must include a ticket reference: `// TODO [TKT-123]: refactor after PGVector upgrade`

## README
The project README must contain:
1. Project overview (one paragraph)
2. Prerequisites (Java 21, Docker, Ollama or OpenAI API key)
3. Local setup instructions (step by step)
4. How to run tests (`./gradlew test`)
5. How to run the app (`./gradlew bootRun`)
6. Environment variables required (names only, never values)
7. Links to spec documents

## AI Mistakes Log
Maintain `docs/ai-mistakes.md` — log every meaningful AI error caught during development:

```markdown
## Mistake N — [Date]
**Type:** Wrong code | Hallucinated answer | Bad test | Incorrect import
**What AI generated:** (paste the wrong snippet)
**Why it was wrong:** (your analysis)
**What you changed:** (correct version)
**Lesson:** (one-line takeaway)
```

At least one code mistake and one RAG/hallucination mistake must be documented before submission.

## Prompt History
Every prompt given to the AI assistant must be saved.

- File: `docs/prompt-history.md`
- Format per entry:
```markdown
## [YYYY-MM-DD HH:MM] — [Short label]
**Prompt:**
> your prompt text here

**Summary of AI response:** one sentence
**Accepted / Modified / Rejected:** state which
```

Also maintain `.specstory/history/` for raw session exports if using SpecStory.

## Architecture Decision Records (ADR)
For significant decisions (embedding model, vector store choice, chunking strategy), write a short ADR in `docs/adr/`:

```markdown
# ADR-001: Embedding Model Selection
**Date:** 2025-01-15
**Status:** Accepted
**Context:** Need to embed ticket text for semantic search
**Decision:** Use text-embedding-3-small via OpenAI
**Consequences:** Requires OpenAI API key; use nomic-embed-text locally via Ollama
```
