# ADR-001: Embedding Model Selection

**Date:** 2026-09-24  
**Status:** Proposed  
**Deciders:** Development team

## Context
The RAG pipeline requires a text embedding model to convert ticket text (descriptions, comments, resolution notes) into vector representations for semantic similarity search.

## Options Considered

| Model | Provider | Dimensions | Latency | Cost | Quality |
|---|---|---|---|---|---|
| `text-embedding-ada-002` | OpenAI | 1536 | ~100ms | $0.0001/1K tokens | High |
| `text-embedding-3-small` | OpenAI | 1536 | ~100ms | $0.00002/1K tokens | High (5× cheaper) |
| `nomic-embed-text` | Ollama (local) | 768 | ~50ms | Free | Good |
| `mxbai-embed-large` | Ollama (local) | 1024 | ~80ms | Free | Better than nomic |

## Decision
**Production:** `text-embedding-3-small` (OpenAI)  
**Local development:** `nomic-embed-text` (Ollama)

## Rationale
- `text-embedding-3-small` offers the best cost/quality ratio — 5× cheaper than ada-002 with comparable or better quality
- `nomic-embed-text` via Ollama allows fully offline local development with no API cost
- Both are configurable via `application.yml` — no code change needed to switch

## Consequences
- Production requires `OPENAI_API_KEY` environment variable
- Local dev requires Ollama running at `http://localhost:11434`
- Embeddings must be re-generated if the model changes (dimensions differ between models)
