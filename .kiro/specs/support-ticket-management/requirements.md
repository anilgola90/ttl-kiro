# Requirements Document

## Introduction

This document describes the requirements for an AI-Powered Support Ticket Management System built on Java 21, Spring Boot, and Spring AI. The system provides full ticket lifecycle management (create, read, update, comment, search, filter) backed by PostgreSQL, enforces a strict state machine for ticket status transitions, and exposes a Retrieval-Augmented Generation (RAG) endpoint that answers natural-language questions grounded exclusively in real ticket data. The frontend is a React/Next.js single-page application that communicates with the backend via a versioned REST API.

---

## Glossary

- **System**: The AI-Powered Support Ticket Management System as a whole.
- **Ticket_Service**: The Spring Boot service layer component responsible for all ticket business logic and state transitions.
- **Ticket_Controller**: The Spring MVC REST controller that handles HTTP concerns for ticket endpoints.
- **Comment_Service**: The service layer component responsible for comment business logic.
- **State_Machine**: The backend-enforced component that governs valid ticket status transitions.
- **Ticket**: A support request entity with an ID (format `TKT-{number}`), title, description, status, priority, assignee, comments, and timestamps.
- **Comment**: A text entry attached to a Ticket, authored by a user, with a timestamp.
- **Status**: The lifecycle stage of a Ticket; one of `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`.
- **Priority**: The urgency level of a Ticket; one of `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- **RAG_Pipeline**: The Retrieval-Augmented Generation component that retrieves semantically relevant ticket chunks and passes them to an LLM to generate a grounded answer.
- **Embedding_Service**: The component responsible for generating and storing vector embeddings of ticket content.
- **Vector_Store**: The PGVector-backed store that holds ticket chunk embeddings for semantic search.
- **Chunk**: A discrete unit of ticket content (title+description, a single comment body, or resolution notes) stored as a vector embedding.
- **Chunk_Metadata**: Structured data stored alongside each embedding: `ticketId`, `chunkType`, `status`, `priority`, `assignee`, `createdAt`.
- **Top_K**: The configurable maximum number of embedding chunks to retrieve per query.
- **Similarity_Threshold**: The configurable minimum cosine similarity score a chunk must exceed to be included in retrieval results.
- **GlobalExceptionHandler**: The `@RestControllerAdvice` component that maps domain exceptions to HTTP error envelopes.
- **UI**: The React/Next.js frontend application.
- **Validator**: The Bean Validation component enforcing input constraints on request DTOs.
- **Flyway**: The schema migration tool used to manage database schema changes.
- **Repository**: The Spring Data JPA component for database access.

---

## Requirements

### Requirement 1: Ticket Creation

**User Story:** As a support agent, I want to create a new support ticket with a title, description, priority, and optional assignee, so that I can track a customer issue from inception.

#### Acceptance Criteria

1. WHEN a `POST /api/v1/tickets` request is received with a valid title (1–200 characters), description (1–5000 characters), and priority, THE Ticket_Service SHALL create a new Ticket with status `OPEN`, generate a unique ID in the format `TKT-{number}`, and persist it to the database.
2. WHEN a Ticket is created, THE System SHALL return HTTP 201 with a `Location` header pointing to the new ticket resource and the full ticket representation in the response body.
3. IF a `POST /api/v1/tickets` request is received with a blank or absent title, THEN THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with an error envelope containing the field name `title` and a violation message.
4. IF a `POST /api/v1/tickets` request is received with a blank or absent description, THEN THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with an error envelope containing the field name `description` and a violation message.
5. IF a `POST /api/v1/tickets` request is received with an absent or invalid priority value, THEN THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with an error envelope listing the accepted priority values (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
6. IF a `POST /api/v1/tickets` request includes an `assignee` field whose value does not correspond to a known user, THEN THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with an error envelope identifying the unrecognised assignee value.
7. WHEN a Ticket is created, THE Embedding_Service SHALL generate and store a vector embedding for the title+description Chunk in the Vector_Store with Chunk_Metadata containing `ticketId`, `chunkType` of `DESCRIPTION`, `status`, `priority`, `assignee`, and `createdAt`.
8. IF the Embedding_Service fails to generate or store the embedding at ticket creation time, THEN THE Ticket_Service SHALL still persist the Ticket and THE System SHALL return HTTP 201, and THE Embedding_Service SHALL log the failure at `ERROR` level with the affected `ticketId`.

---

### Requirement 2: List Tickets

**User Story:** As a support agent, I want to list all tickets with optional filtering and pagination, so that I can manage my queue efficiently.

#### Acceptance Criteria

1. WHEN a `GET /api/v1/tickets` request is received, THE Ticket_Controller SHALL return a paginated list of tickets with fields: `id`, `title`, `status`, `priority`, `assignee`, `createdAt`, `updatedAt`, sorted by `createdAt` descending by default.
2. WHEN a `GET /api/v1/tickets?status={status}` request is received with a valid status value (one of `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`), THE Ticket_Service SHALL return only tickets whose status matches the provided value.
3. WHEN a `GET /api/v1/tickets?search={keyword}` request is received with a keyword of 1–100 characters, THE Ticket_Service SHALL return only tickets whose title or description contains the provided keyword (case-insensitive).
4. WHEN a `GET /api/v1/tickets` request is received with both `status` and `search` query parameters, THE Ticket_Service SHALL apply both filters simultaneously and return only tickets satisfying both conditions.
5. WHEN a `GET /api/v1/tickets` request is received without pagination parameters, THE System SHALL default to `page=0` and `size=20` (maximum `size` of 100) and include `page`, `size`, `totalElements`, and `totalPages` in the response envelope.
6. IF a `GET /api/v1/tickets?status={status}` request is received with a value not in (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`), THEN THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with an error envelope.
7. IF a `GET /api/v1/tickets?search={keyword}` request is received with a keyword exceeding 100 characters, THEN THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with an error envelope.

---

### Requirement 3: View Ticket Details

**User Story:** As a support agent, I want to view the full details of a specific ticket including all comments, so that I have complete context when working on an issue.

#### Acceptance Criteria

1. WHEN a `GET /api/v1/tickets/{id}` request is received with an existing ticket ID, THE Ticket_Controller SHALL return HTTP 200 with the full ticket representation including all comments ordered by `createdAt` ascending.
2. WHEN a `GET /api/v1/tickets/{id}` request is received with a non-existent ticket ID, THE GlobalExceptionHandler SHALL return HTTP 404 with an error envelope containing the unrecognised ID.
3. THE System SHALL include the following fields in the ticket detail response: `id`, `title`, `description`, `status`, `priority`, `assignee`, `createdAt`, `updatedAt`, and an array of `comments` each containing `id`, `body`, `author`, and `createdAt`.

---

### Requirement 4: Update Ticket Fields

**User Story:** As a support agent, I want to update a ticket's title, description, priority, and assignee, so that I can keep ticket information accurate as an issue evolves.

#### Acceptance Criteria

1. WHEN a `PATCH /api/v1/tickets/{id}` request is received with one or more of `title`, `description`, `priority`, or `assignee` fields, THE Ticket_Service SHALL update only the supplied fields and leave unspecified fields unchanged.
2. WHEN a `PATCH /api/v1/tickets/{id}` request is received for a non-existent ticket ID, THE GlobalExceptionHandler SHALL return HTTP 404 with an error envelope.
3. WHEN a `PATCH /api/v1/tickets/{id}` request is received with a blank title or blank description (when those fields are included in the payload), THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with a field-level error envelope.
4. WHEN a Ticket's title or description is updated, THE Embedding_Service SHALL delete all existing chunks for that ticket from the Vector_Store and re-generate and store new embeddings reflecting the updated content.
5. WHEN a Ticket field update succeeds, THE System SHALL return HTTP 200 with the full updated ticket representation and an updated `updatedAt` timestamp.

---

### Requirement 5: Ticket State Machine

**User Story:** As a system administrator, I want the ticket lifecycle to be enforced by a backend state machine, so that tickets always progress through valid states and no invalid transitions are permitted.

#### Acceptance Criteria

1. WHEN the State_Machine receives a transition request from `OPEN` to `IN_PROGRESS`, THE Ticket_Service SHALL accept the transition and persist the new status.
2. WHEN the State_Machine receives a transition request from `IN_PROGRESS` to `RESOLVED`, THE Ticket_Service SHALL accept the transition and persist the new status.
3. WHEN the State_Machine receives a transition request from `RESOLVED` to `CLOSED`, THE Ticket_Service SHALL accept the transition and persist the new status.
4. WHEN the State_Machine receives a transition request from `OPEN` to `CANCELLED`, THE Ticket_Service SHALL accept the transition and persist the new status.
5. WHEN the State_Machine receives a transition request from `IN_PROGRESS` to `CANCELLED`, THE Ticket_Service SHALL accept the transition and persist the new status.
6. WHEN the State_Machine receives a transition request to any status that is not a valid next state for the current status, THE Ticket_Service SHALL reject the transition, leave the ticket status unchanged, and THE GlobalExceptionHandler SHALL return HTTP 422 with an error envelope containing the `INVALID_TRANSITION` error code, the current status, and the attempted target status.
7. IF the current ticket status is `CLOSED` or `CANCELLED`, THEN THE State_Machine SHALL reject any transition request to any target status and return HTTP 422; IF the current status is `RESOLVED`, THEN transitions to `OPEN` or `IN_PROGRESS` SHALL be rejected with HTTP 422.
8. WHEN a `PATCH /api/v1/tickets/{id}/status` request is received with a valid transition, THE System SHALL return HTTP 200 with the updated ticket representation.
9. WHEN a Ticket transitions to `RESOLVED` or `CLOSED` and resolution notes are present, THE Embedding_Service SHALL delete all existing chunks for that ticket and re-ingest a full set of chunks including a Chunk with `chunkType` of `RESOLUTION`.
10. WHEN a `PATCH /api/v1/tickets/{id}/status` request is received for a non-existent ticket ID, THE GlobalExceptionHandler SHALL return HTTP 404 with an error envelope containing the unrecognised ID.
11. IF the Embedding_Service fails during re-ingestion triggered by a `RESOLVED` or `CLOSED` transition, THEN THE Ticket_Service SHALL still persist the status change, THE System SHALL return HTTP 200, and THE Embedding_Service SHALL log the re-ingestion failure at `ERROR` level with the affected `ticketId`.

---

### Requirement 6: Add Comments

**User Story:** As a support agent, I want to add comments to a ticket, so that I can record updates, findings, and communications related to an issue.

#### Acceptance Criteria

1. WHEN a `POST /api/v1/tickets/{id}/comments` request is received with a non-blank `body` and `author` for an existing ticket, THE Comment_Service SHALL create the comment, persist it associated with the ticket, and return HTTP 201 with the created comment representation.
2. WHEN a `POST /api/v1/tickets/{id}/comments` request is received with a blank or absent `body`, THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with a field-level error envelope.
3. WHEN a `POST /api/v1/tickets/{id}/comments` request is received for a non-existent ticket ID, THE GlobalExceptionHandler SHALL return HTTP 404 with an error envelope.
4. WHEN a comment is added to a Ticket, THE Embedding_Service SHALL asynchronously generate and store a new vector embedding for that comment as a separate Chunk with `chunkType` of `COMMENT` in the Vector_Store; IF the embedding generation fails, THE Comment_Service SHALL still return HTTP 201 for the comment creation and THE Embedding_Service SHALL log the failure at `WARN` level.
5. WHEN a comment is added to a Ticket, THE System SHALL update the ticket's `updatedAt` timestamp.

---

### Requirement 7: Search and Filter Tickets

**User Story:** As a support agent, I want to search tickets by keyword and filter by status, so that I can quickly locate relevant tickets without browsing the full list.

#### Acceptance Criteria

1. WHEN a `GET /api/v1/tickets?search={keyword}` request is received with a non-blank keyword, THE Ticket_Service SHALL perform a case-insensitive match against ticket titles and descriptions and return all matching tickets in the paginated response envelope.
2. WHEN a `GET /api/v1/tickets?search={keyword}` request is received and no tickets match the keyword, THE System SHALL return HTTP 200 with an empty `data` array and `totalElements` equal to 0.
3. WHEN a `GET /api/v1/tickets?status={status}` request is received with a valid status value, THE Ticket_Service SHALL return only tickets in that status in the paginated response envelope.
4. WHEN a `GET /api/v1/tickets?search={keyword}&status={status}` request is received, THE Ticket_Service SHALL apply both the keyword filter and the status filter and return only tickets satisfying both conditions.
5. IF a `GET /api/v1/tickets?search=` request is received with an empty keyword value, THEN THE Ticket_Service SHALL treat it identically to a request with no `search` parameter and return all tickets unfiltered.

---

### Requirement 8: Input Validation and Error Responses

**User Story:** As a frontend developer, I want the backend to return structured, actionable error messages for all validation failures, so that the UI can display meaningful feedback to users.

#### Acceptance Criteria

1. THE GlobalExceptionHandler SHALL return a consistent error envelope for all error responses containing the fields `error` (machine-readable code), `message` (human-readable description), `timestamp` (ISO-8601 UTC), and `path` (request URI).
2. WHEN Bean Validation fails on a request DTO, THE GlobalExceptionHandler SHALL return HTTP 400 and include all field-level violations in the `message` field.
3. THE System SHALL never include a Java stack trace in any API error response.
4. WHEN the System encounters an unhandled internal exception, THE GlobalExceptionHandler SHALL return HTTP 500 with the `INTERNAL_ERROR` error code and a generic message that does not expose implementation details.
5. IF a request body cannot be parsed as valid JSON, THEN THE GlobalExceptionHandler SHALL return HTTP 400 with an error envelope containing a message indicating malformed JSON.

---

### Requirement 9: Ticket Embedding and Re-Ingestion

**User Story:** As a system operator, I want ticket embeddings to be automatically maintained in the vector store whenever ticket content changes, so that AI-powered search always reflects the latest ticket data.

#### Acceptance Criteria

1. WHEN a Ticket is first created, THE Embedding_Service SHALL generate a Chunk from the ticket title and description, embed it using the configured embedding model, and store it in the Vector_Store with Chunk_Metadata including `ticketId`, `chunkType` of `DESCRIPTION`, `status`, `priority`, `assignee`, and `createdAt`.
2. WHEN a Ticket's title, description, priority, or assignee is updated, THE Embedding_Service SHALL attempt to delete all existing chunks identified by the ticket's `ticketId` metadata from the Vector_Store before inserting the new embeddings; IF the deletion fails, THE Embedding_Service SHALL still proceed to generate and store the new embeddings and SHALL log a warning indicating that stale chunks may be present.
3. WHEN a comment is added to a Ticket, THE Embedding_Service SHALL embed the comment body as a separate Chunk with `chunkType` of `COMMENT` and store it in the Vector_Store without deleting existing chunks for the ticket.
4. WHEN a Ticket transitions to `RESOLVED` or `CLOSED`, THE Embedding_Service SHALL delete all existing chunks for that ticket and re-ingest a full set of chunks including a resolution notes Chunk with `chunkType` of `RESOLUTION`.
5. THE Embedding_Service SHALL use `text-embedding-3-small` via OpenAI when the `prod` profile is active and `nomic-embed-text` via Ollama when the `dev` profile is active.
6. WHILE the `dev` profile is active, THE Embedding_Service SHALL connect to Ollama at the URL configured in `app.ai.embedding.ollama-base-url` and SHALL NOT require an OpenAI API key.
7. IF the embedding model is unavailable or returns an error, THEN THE Embedding_Service SHALL log the error at `ERROR` level and propagate a domain exception so that the calling operation fails with HTTP 500 rather than silently persisting a ticket without embeddings.

---

### Requirement 10: AI-Powered Q&A (RAG Endpoint)

**User Story:** As a support manager, I want to ask natural-language questions about past tickets and receive grounded answers citing the specific tickets used, so that I can quickly surface patterns and institutional knowledge from ticket history.

#### Acceptance Criteria

1. WHEN a `POST /api/v1/ai/ask` request is received with a non-blank `question` of 1–1000 characters, THE RAG_Pipeline SHALL retrieve the top-K (default 5, configurable) most semantically similar Chunks from the Vector_Store whose cosine similarity score exceeds the Similarity_Threshold (default 0.75, configurable).
2. WHEN at least one Chunk exceeds the Similarity_Threshold, THE RAG_Pipeline SHALL pass the retrieved chunks as context to the configured LLM and return a response containing `answer`, `sources` (array of `ticketId` strings), and `grounded: true`.
3. WHEN no Chunks exceed the Similarity_Threshold, THE RAG_Pipeline SHALL return HTTP 200 with `answer` set to `"No relevant tickets were found to answer this question."`, an empty `sources` array, and `grounded: false` without invoking the LLM.
4. THE RAG_Pipeline SHALL use a system prompt that instructs the LLM to answer exclusively from the provided ticket context and to cite the `ticketId`(s) used in its answer.
5. THE RAG_Pipeline SHALL set the LLM generation temperature to `0.0` to produce deterministic, grounded responses.
6. IF a `POST /api/v1/ai/ask` request is received with a blank, whitespace-only, or absent `question`, OR with a `question` exceeding 1000 characters, THEN THE Validator SHALL reject the request and THE System SHALL return HTTP 400 with an error envelope.
7. THE System SHALL execute a single retrieval-then-generate flow per request; THE RAG_Pipeline SHALL NOT make autonomous follow-up retrievals or tool calls.
8. THE System SHALL log the first 200 characters of the incoming question and the retrieved Chunk IDs at `INFO` level for auditability.
9. IF the LLM call fails or times out after chunks have been retrieved, THEN THE RAG_Pipeline SHALL return HTTP 500 with an error envelope containing the `LLM_ERROR` error code and a generic message; THE System SHALL NOT return partial or fabricated content.

---

### Requirement 11: RAG Configuration

**User Story:** As a system operator, I want to configure Top-K, similarity threshold, and embedding model settings via application properties, so that I can tune retrieval quality without modifying source code.

#### Acceptance Criteria

1. THE System SHALL bind the `app.ai.retrieval.top-k` property to the Top_K parameter used by the RAG_Pipeline, with a default value of `5`.
2. THE System SHALL bind the `app.ai.retrieval.similarity-threshold` property to the Similarity_Threshold used by the RAG_Pipeline, with a default value of `0.75`.
3. THE System SHALL bind the `app.ai.embedding.model` property to the embedding model name, defaulting to `text-embedding-3-small` for the `prod` profile and `nomic-embed-text` for the `dev` profile.
4. THE System SHALL bind the `app.ai.generation.model` property to the LLM model name, defaulting to `gpt-4o-mini` for the `prod` profile and `llama3.2` for the `dev` profile.
5. THE System SHALL bind the `app.ai.generation.temperature` property to the LLM temperature setting, defaulting to `0.0`.
6. IF an unrecognised or out-of-range value is provided for `app.ai.retrieval.top-k` (e.g. negative integer), THEN THE System SHALL fail fast at application startup with a descriptive configuration error.
7. THE System SHALL never hardcode the OpenAI API key, Ollama base URL, or any other secret in source code, AND THE System SHALL require all credentials to be supplied via environment variables; IF either condition is violated THEN THE System SHALL fail to start with a descriptive configuration error.

---

### Requirement 12: UI — Ticket List and Search

**User Story:** As a support agent using the web interface, I want to see a list of tickets with search and filter controls, so that I can manage my queue without using the API directly.

#### Acceptance Criteria

1. WHEN the UI renders the ticket list page, THE UI SHALL display each ticket's `id`, `title`, `status`, `priority`, `assignee`, and `createdAt` in a tabular or card layout.
2. WHEN a support agent types in the search field and submits, THE UI SHALL send a `GET /api/v1/tickets?search={keyword}` request and display the returned tickets.
3. WHEN a support agent selects a status from the filter dropdown, THE UI SHALL send a `GET /api/v1/tickets?status={status}` request and update the displayed list.
4. WHEN the API returns a validation error (HTTP 400) or a not-found error (HTTP 404), THE UI SHALL display the `message` field from the error envelope in a visible error notification.
5. WHEN the API returns no tickets matching the current filters, THE UI SHALL display a "No tickets found" message instead of an empty table.
6. THE UI SHALL support pagination controls that pass `page` and `size` query parameters to the list endpoint and display `totalPages` navigation.

---

### Requirement 13: UI — Ticket Detail and State Transitions

**User Story:** As a support agent using the web interface, I want to view full ticket details, change status, add comments, and update fields from a single ticket detail page, so that I can manage a ticket end-to-end without switching contexts.

#### Acceptance Criteria

1. WHEN a support agent navigates to a ticket detail page, THE UI SHALL display all fields returned by `GET /api/v1/tickets/{id}` including the full comment history ordered oldest-first.
2. WHEN a support agent selects a new status from the status control and confirms, THE UI SHALL send a `PATCH /api/v1/tickets/{id}/status` request and, upon HTTP 200, update the displayed status without a full page reload.
3. WHEN the API returns HTTP 422 for an invalid state transition, THE UI SHALL display the `message` from the error envelope in a visible error notification and leave the current status unchanged.
4. WHEN a support agent submits the add-comment form with a non-blank body, THE UI SHALL send a `POST /api/v1/tickets/{id}/comments` request and, upon HTTP 201, append the new comment to the comment list without a full page reload.
5. WHEN a support agent edits one or more fields (title, description, priority, assignee) and saves, THE UI SHALL send a `PATCH /api/v1/tickets/{id}` request with only the modified fields and, upon HTTP 200, update the displayed values.
6. WHEN any API call from the ticket detail page returns an error, THE UI SHALL display the `message` from the error envelope in a visible error notification and not discard unsaved user input.

---

### Requirement 14: UI — AI Ask Interface

**User Story:** As a support manager using the web interface, I want an AI chat panel where I can ask questions about past tickets and see cited answers, so that I can gain insights from ticket history interactively.

#### Acceptance Criteria

1. WHEN the AI ask panel is visible and a support manager submits a non-blank question, THE UI SHALL send a `POST /api/v1/ai/ask` request with the `question` field and display a loading indicator while waiting.
2. WHEN the API responds with `grounded: true`, THE UI SHALL display the `answer` text and render each entry in the `sources` array as a clickable link to the corresponding ticket detail page.
3. WHEN the API responds with `grounded: false`, THE UI SHALL display the `answer` text (the no-match phrase) without rendering any source links.
4. WHEN the API returns an error response, THE UI SHALL display the `message` from the error envelope in the chat panel.
5. WHEN a support manager submits a blank question, THE UI SHALL prevent the request from being sent and display a "Question cannot be empty" validation message inline.

---

### Requirement 15: Non-Functional Requirements

**User Story:** As a system architect, I want the system to meet baseline quality attributes for persistence, security, observability, and schema management, so that the system is production-ready and maintainable.

#### Acceptance Criteria

1. THE System SHALL use Flyway to manage all database schema migrations; THE System SHALL never use `spring.jpa.hibernate.ddl-auto=create` in the `prod` profile.
2. THE System SHALL use H2 in-memory database for automated tests and PostgreSQL with PGVector for the `dev` and `prod` profiles.
3. THE System SHALL never commit secrets (API keys, database passwords, tokens) to source control; all secrets SHALL be supplied via environment variables.
4. WHEN the application starts, THE System SHALL validate all `@ConfigurationProperties` bindings and fail fast with a descriptive error if required properties are missing or out of range.
5. THE System SHALL expose an OpenAPI specification at `/v3/api-docs` and a Swagger UI at `/swagger-ui.html` when the `dev` or `test` profile is active.
6. THE System SHALL use SLF4J with Logback for all logging; THE System SHALL never log sensitive data including API keys, passwords, or personally identifiable information.
7. WHERE CORS is configured, THE System SHALL specify explicit allowed origins and SHALL NOT use the wildcard `*` in the `prod` profile.
8. THE System SHALL use Spring Data Auditing (`@CreatedDate`, `@LastModifiedDate`) to automatically populate `createdAt` and `updatedAt` on all entities.
