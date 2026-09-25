# RAG / Vector Store Guidelines

## Overview
This project uses a Retrieval-Augmented Generation (RAG) pipeline to answer natural-language questions grounded strictly in support ticket data. The assistant must never fabricate answers from general LLM knowledge.

---

## Chunking Strategy

### Chosen Approach: Semantic / Paragraph-Based Chunking
For support ticket data, use semantic chunking — split on logical boundaries (title, description paragraph, each comment, resolution notes) rather than fixed character counts.

**Rationale:**
- Tickets are short, structured documents — fixed-size chunking risks splitting mid-sentence across a description
- Each semantic section (description, comment, resolution) has distinct retrieval value
- Preserves coherent context for the LLM at generation time

### Chunk Schema per Ticket
```
Chunk 1: [Title] + [Description]
Chunk 2..N: [Each comment body] (one chunk per comment)
Chunk N+1: [Resolution notes] (if status = RESOLVED or CLOSED)
```

### Chunk Metadata (stored alongside each embedding)
```json
{
  "ticketId": "TKT-1001",
  "chunkType": "DESCRIPTION | COMMENT | RESOLUTION",
  "status": "RESOLVED",
  "priority": "HIGH",
  "assignee": "jane.doe",
  "category": "PAYMENT",
  "createdAt": "2025-01-15T10:30:00Z"
}
```

### What NOT to do
- Do NOT chunk by fixed 512-token windows across a whole ticket — this loses structural boundaries
- Do NOT embed the raw JSON entity — embed human-readable prose
- Do NOT skip metadata — it enables filtered retrieval

---

## Embedding Model Choice

### Chosen Model: `text-embedding-ada-002` (OpenAI) or `nomic-embed-text` (Ollama local)

| Option | Dimensions | Latency | Cost | Quality |
|---|---|---|---|---|
| OpenAI `text-embedding-ada-002` | 1536 | ~100ms | ~$0.0001/1K tokens | High |
| OpenAI `text-embedding-3-small` | 1536 | ~100ms | ~$0.00002/1K tokens | High (cheaper) |
| Ollama `nomic-embed-text` | 768 | ~50ms local | Free | Good for local dev |
| Ollama `mxbai-embed-large` | 1024 | ~80ms local | Free | Better than nomic |

**Default for production:** `text-embedding-3-small` — best cost/quality ratio  
**Default for local dev:** `nomic-embed-text` via Ollama — no API cost, runs offline

### Configuration (bound via `@ConfigurationProperties`)
```yaml
app:
  ai:
    embedding:
      model: text-embedding-3-small   # override with nomic-embed-text for local
      provider: openai                 # openai | ollama
      ollama-base-url: http://localhost:11434
    retrieval:
      top-k: 5
      similarity-threshold: 0.75
    generation:
      model: gpt-4o-mini              # override for local: llama3.2
      temperature: 0.0                # deterministic — no creativity for grounded answers
```

---

## Vector Store

### Chosen Store: PGVector (production) / In-memory SimpleVectorStore (test)
- PGVector runs alongside PostgreSQL — single infrastructure dependency
- Use Testcontainers `pgvector/pgvector` Docker image in integration tests
- Chroma is acceptable as an alternative if PGVector is unavailable

### Index Configuration
- Distance metric: **cosine similarity** (best for semantic text)
- Index type: `ivfflat` for large collections, `hnsw` for better recall at query time
- Always create index after bulk ingestion, not before

---

## Retrieval Tuning Defaults

| Parameter | Default | Notes |
|---|---|---|
| `top-k` | 5 | Return top 5 most similar chunks |
| `similarity-threshold` | 0.75 | Discard chunks below this score |
| Max context tokens | 3000 | Stay within LLM context window |

- If 0 chunks exceed the threshold → return "no relevant tickets found" — do NOT pass empty context to LLM
- Both `top-k` and `similarity-threshold` MUST be configurable via `application.yml` — never hardcoded

---

## Re-Ingestion Rules
- Trigger re-ingestion on: ticket update, comment added, status changed to RESOLVED/CLOSED
- Delete old chunks for the ticket by `ticketId` metadata before inserting new ones (upsert pattern)
- Re-ingestion must be synchronous within the same transaction or async via a Spring `@EventListener`
- Stale embeddings are a correctness bug — treat as P1

---

## Grounding & Guardrails

### System Prompt Template
```
You are a support assistant. Answer ONLY using the ticket context provided below.
Do NOT use any general knowledge outside of these tickets.
If the provided context does not contain enough information to answer the question, 
respond exactly with: "No relevant tickets were found to answer this question."
Always cite the ticketId(s) you used.

Context:
{retrieved_chunks}

Question: {user_question}
```

### Guardrail Rules
1. If `sources` list is empty → response MUST be the no-match phrase, not a generated answer
2. LLM temperature MUST be 0.0 for grounded responses
3. Never chain tool calls or take autonomous actions — single retrieval → generate only
4. Log both the question and the retrieved chunk IDs for auditability

---

## Evaluation Strategy
- **Precision@K**: % of retrieved chunks that are actually relevant
- **Recall**: Are all relevant tickets retrieved?
- **Faithfulness**: Does the answer contain only facts from the retrieved chunks?
- **Answer Relevance**: Does the answer address the question?
- Use RAGAS framework or manual spot-checks for evaluation
- Document at least one hallucination or retrieval failure caught during development in `docs/ai-mistakes.md`
