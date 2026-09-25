# AI-Powered Support Ticket Management System

A full-stack support ticket platform that manages the complete ticket lifecycle through a strictly enforced state machine and answers natural-language questions grounded exclusively in ticket data. The backend is built with **Java 21** and **Spring Boot**, persists tickets and comments in **PostgreSQL** and stores vector embeddings in **PGVector**. All AI behaviour (embedding, retrieval, generation) is driven by **Spring AI** and exposed through a Retrieval-Augmented Generation (RAG) endpoint (`POST /api/v1/ai/ask`) that never falls through to general LLM knowledge — if no ticket chunks pass the similarity threshold, it returns a "no relevant tickets found" response instead of a fabricated answer. A **Next.js** frontend provides the ticket list, ticket detail, and AI Ask UI.

## Prerequisites

- **Java 21** (Gradle toolchain is pinned to language version 21)
- **Docker** — required to run PostgreSQL + PGVector locally and to run the Testcontainers-based integration tests
- **Node.js 20+** — for the Next.js frontend
- One of the following AI providers:
  - **Ollama** (local development) — with the `nomic-embed-text` (embeddings) and `llama3.2` (generation) models pulled, **or**
  - **OpenAI API key** (production) — used for `text-embedding-3-small` (embeddings) and `gpt-4o-mini` (generation)

## Architecture & Specification Documents

The system design is documented under the spec folder. Start here to understand requirements, architecture, and the implementation plan:

- [`.kiro/specs/support-ticket-management/requirements.md`](.kiro/specs/support-ticket-management/requirements.md) — functional and non-functional requirements
- [`.kiro/specs/support-ticket-management/design.md`](.kiro/specs/support-ticket-management/design.md) — architecture, component diagram, data models, RAG pipeline, and correctness properties
- [`.kiro/specs/support-ticket-management/tasks.md`](.kiro/specs/support-ticket-management/tasks.md) — the incremental implementation plan

Cross-cutting engineering conventions live in the steering folder:

- [`.kiro/steering/`](.kiro/steering/) — `java-springboot.md`, `api-standards.md`, `rag-vector-store.md`, `testing.md`, and `documentation.md`

Architecture Decision Records are under [`docs/adr/`](docs/adr/) (e.g. embedding model selection).

## Local Setup

### 1. Start PostgreSQL + PGVector via Docker

```bash
docker run --name ticketdb \
  -e POSTGRES_DB=ticketdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=<your-password> \
  -p 5432:5432 \
  -d pgvector/pgvector:pg16
```

This starts a PostgreSQL 16 instance with the `pgvector` extension available. Flyway migrations enable the extension and create the schema on first application start.

### 2. Set environment variables

The application reads all secrets and environment-specific values from environment variables — nothing is committed. Set the following (names only; supply your own values):

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `OPENAI_API_KEY` (production profile only)
- `CORS_ALLOWED_ORIGINS`

### 3. (dev profile) Run Ollama and pull the local models

For local development the `dev` profile uses Ollama instead of OpenAI. With Ollama running:

```bash
ollama pull nomic-embed-text
ollama pull llama3.2
```

Ollama is expected at `http://localhost:11434` (configurable via `app.ai.embedding.ollama-base-url`).

### 4. Run the backend

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The API starts on `http://localhost:8080`. Use `--spring.profiles.active=prod` (with `OPENAI_API_KEY` set) to run against OpenAI.

### 5. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The Next.js dev server starts on `http://localhost:3000`.

## Running Tests

```bash
# Unit tests, @WebMvcTest slice tests, and jqwik property tests (H2 in-memory DB)
./gradlew test

# Testcontainers-backed integration tests (real PostgreSQL + PGVector) — requires Docker.
# These run as part of the standard test task; a dedicated `integrationTest` task is used
# to run the Testcontainers suites in isolation where configured.
./gradlew integrationTest
```

Notes:

- Unit, slice, and property-based tests run against an **H2** in-memory database and do not require Docker.
- Integration tests use **Testcontainers** to spin up a real PostgreSQL + PGVector container, so **Docker must be running** for them to pass.
- The ticket state machine is verified to **100% branch coverage** via **JaCoCo** (`jacocoTestCoverageVerification` enforces the threshold on `TicketStateMachine`), and the service layer targets ≥ 90% line coverage.

## Required Environment Variables

No secrets are committed to the repository — all values are supplied via environment variables. Only the variable **names** are documented here.

| Variable | Description |
|----------|-------------|
| `DB_URL` | JDBC URL for the PostgreSQL + PGVector database (e.g. the `ticketdb` instance on port 5432) |
| `DB_USERNAME` | Database username (defaults to `postgres` if unset) |
| `DB_PASSWORD` | Database password |
| `OPENAI_API_KEY` | OpenAI API key used for embeddings and generation in the `prod` profile |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins for CORS (e.g. the frontend URL) |

## API Overview

All endpoints are prefixed with `/api/v1/`. Timestamps are ISO-8601 UTC and ticket IDs use the `TKT-{number}` format.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/tickets` | Create a ticket (201 + `Location` header) |
| `GET` | `/api/v1/tickets` | List/filter tickets (`?status`, `?search`, `?page`, `?size`) |
| `GET` | `/api/v1/tickets/{id}` | Get a single ticket |
| `PATCH` | `/api/v1/tickets/{id}` | Partial update of ticket fields |
| `PATCH` | `/api/v1/tickets/{id}/status` | Transition ticket status (enforced by the state machine) |
| `POST` | `/api/v1/tickets/{id}/comments` | Add a comment to a ticket |
| `POST` | `/api/v1/ai/ask` | Ask a natural-language question answered from ticket data (RAG) |

Interactive API documentation (Swagger UI) is available at **`/swagger-ui.html`** in the `dev` and `test` profiles only.

## Security & Secrets

No secrets are committed to this repository. Database credentials and the OpenAI API key are provided exclusively through environment variables and resolved at runtime (see [Required Environment Variables](#required-environment-variables)). `.env` files are git-ignored.
