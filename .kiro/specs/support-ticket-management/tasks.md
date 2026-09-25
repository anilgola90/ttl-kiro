# Implementation Plan: AI-Powered Support Ticket Management System

## Overview

Implement the full-stack support ticket management system in phases, building from the
foundation upward: Gradle configuration → domain model + Flyway migrations → service layer
(state machine, ticket, comment) → exception handling → REST controllers → embedding and RAG
pipeline → tests → Next.js frontend → documentation. Each phase produces tested, runnable
code that integrates with what came before.

---

## Tasks

- [x] 1. Project setup and build configuration
  - [x] 1.1 Update `build.gradle` with all required dependencies
    - Add Spring Boot starters: `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-web`
    - Add Flyway: `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`
    - Add PostgreSQL driver: `org.postgresql:postgresql`
    - Add Spring AI BOM and starters: `spring-ai-bom`, `spring-ai-openai-spring-boot-starter`, `spring-ai-ollama-spring-boot-starter`, `spring-ai-pgvector-store-spring-boot-starter`
    - Add Lombok (already present — confirm annotation processor is wired)
    - Add SpringDoc OpenAPI: `springdoc-openapi-starter-webmvc-ui`
    - Add test dependencies: `spring-boot-testcontainers`, `testcontainers` (postgresql, pgvector BOM), `jqwik` (net.jqwik:jqwik), AssertJ
    - Add JaCoCo plugin for coverage enforcement
    - _Requirements: 15.2, 15.5_

  - [x] 1.2 Create `application.yml` and profile-specific YAML files
    - Create `src/main/resources/application.yml`: datasource, Flyway enabled, JPA `ddl-auto: validate`, Spring Data Auditing enabled, `app.ai.*` defaults, CORS explicit allowed origins
    - Create `src/main/resources/application-dev.yml`: Ollama embedding (`nomic-embed-text`, 768 dims), `llama3.2` generation model, datasource pointing to local PostgreSQL
    - Create `src/main/resources/application-prod.yml`: OpenAI embedding (`text-embedding-3-small`, 1536 dims), `gpt-4o-mini` generation model, `ddl-auto: validate`, no wildcard CORS
    - Create `src/test/resources/application-test.yml`: H2 in-memory, `ddl-auto: create-drop`, Spring AI mock/simple vector store, disable Flyway for slice tests
    - Secrets (`OPENAI_API_KEY`, `DB_PASSWORD`) referenced via `${ENV_VAR}` only — never hardcoded
    - _Requirements: 9.5, 9.6, 11.1–11.7, 15.1, 15.3, 15.7_

  - [x] 1.3 Create `AppAiProperties` configuration-properties class
    - Create `com.example.kirotest.config.AppAiProperties` as a `@ConfigurationProperties(prefix = "app.ai")` `@Validated` record with nested `Embedding`, `Retrieval`, `Generation` records
    - `Embedding`: `@NotBlank provider`, `@NotBlank model`, `ollamaBaseUrl`, `@Min(1) int dimensions`
    - `Retrieval`: `@Min(1) @Max(100) int topK`, `@DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold`
    - `Generation`: `@NotBlank String model`, `@DecimalMin("0.0") @DecimalMax("2.0") double temperature`
    - Register with `@EnableConfigurationProperties(AppAiProperties.class)` in `AiConfig`
    - _Requirements: 11.1–11.6, 15.4_

  - [x] 1.4 Create `AiConfig`, `WebConfig`, and `OpenApiConfig` Spring configuration classes
    - `AiConfig`: declare `EmbeddingModel`, `ChatModel`, and `PGVectorStore` beans; use `@Profile` to select OpenAI vs Ollama beans
    - `WebConfig`: configure `CorsRegistry` with explicit allowed origins from properties; implement `@EnableSpringDataWebSupport`
    - `OpenApiConfig`: configure SpringDoc `GroupedOpenApi`, set API title/version; expose Swagger UI only on `dev`/`test` profiles
    - Enable Spring Data Auditing with `@EnableJpaAuditing` in main config class
    - _Requirements: 15.5, 15.7_

- [x] 2. Domain model, database schema, and repositories
  - [x] 2.1 Create domain enums and JPA entities
    - Create `com.example.kirotest.domain.TicketStatus` enum: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`
    - Create `com.example.kirotest.domain.Priority` enum: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
    - Create `Ticket` entity with Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, `@EntityListeners(AuditingEntityListener.class)`, all fields from design (id VARCHAR(20), title, description, status, priority, assignee, resolutionNotes, comments OneToMany, `@CreatedDate createdAt`, `@LastModifiedDate updatedAt`)
    - Create `Comment` entity with UUID PK (auto-generated), ManyToOne to Ticket, body, author, `@CreatedDate createdAt`
    - _Requirements: 1.1, 3.3, 15.8_

  - [x] 2.2 Write Flyway SQL migrations (V1–V5)
    - `V1__create_ticket_id_sequence.sql`: `CREATE SEQUENCE ticket_id_seq START WITH 1001 INCREMENT BY 1`
    - `V2__create_tickets_table.sql`: tickets table with all columns, indexes on `status` and `createdAt DESC`
    - `V3__create_comments_table.sql`: comments table, FK to tickets with ON DELETE CASCADE, index on `ticket_id`
    - `V4__enable_pgvector.sql`: `CREATE EXTENSION IF NOT EXISTS vector`
    - `V5__create_vector_store.sql`: document the HNSW index creation strategy (post-ingestion); do NOT duplicate the table DDL managed by Spring AI
    - Place all migration files under `src/main/resources/db/migration/`
    - _Requirements: 15.1_

  - [x] 2.3 Create `TicketRepository` and `CommentRepository`
    - `TicketRepository extends JpaRepository<Ticket, String>`: add `findByStatus(TicketStatus, Pageable)`, `searchByKeyword(@Query JPQL, Pageable)`, `searchByKeywordAndStatus(@Query JPQL, Pageable)`, `nextIdValue()` native query
    - `CommentRepository extends JpaRepository<Comment, UUID>`: add `findByTicketIdOrderByCreatedAtAsc(String)`
    - _Requirements: 2.1–2.5, 7.1–7.5_

- [x] 3. Request/response DTOs and event classes
  - [x] 3.1 Create all request and response DTO records
    - Under `com.example.kirotest.dto.request`: `CreateTicketRequest`, `UpdateTicketRequest`, `UpdateStatusRequest`, `AddCommentRequest`, `AskRequest` — all as Java records with Bean Validation annotations matching design
    - Under `com.example.kirotest.dto.response`: `TicketSummaryResponse`, `TicketResponse`, `CommentResponse`, `PagedResponse<T>`, `AskResponse`, `ErrorResponse` — all as Java records
    - Add `@Schema` annotations to each record field for OpenAPI documentation
    - _Requirements: 1.1, 1.2, 2.1, 3.3, 8.1_

  - [x] 3.2 Create Spring ApplicationEvent classes
    - Create `com.example.kirotest.event.TicketCreatedEvent(Ticket ticket)`
    - Create `com.example.kirotest.event.TicketUpdatedEvent(Ticket ticket)`
    - Create `com.example.kirotest.event.CommentAddedEvent(Comment comment, Ticket ticket)`
    - _Requirements: 1.7, 4.4, 6.4_

- [x] 4. Exception classes and GlobalExceptionHandler
  - [x] 4.1 Create domain exception classes
    - `TicketNotFoundException(String ticketId)` extends `RuntimeException`
    - `InvalidTransitionException(TicketStatus from, TicketStatus to)` extends `RuntimeException`
    - `EmbeddingException(String ticketId, Throwable cause)` extends `RuntimeException`
    - `LlmException(String message, Throwable cause)` extends `RuntimeException`
    - _Requirements: 3.2, 4.2, 5.6, 8.4, 10.9_

  - [x] 4.2 Implement `GlobalExceptionHandler`
    - Create `com.example.kirotest.exception.GlobalExceptionHandler` with `@RestControllerAdvice` and `@Slf4j`
    - Map `TicketNotFoundException` → 404 with `TICKET_NOT_FOUND` code
    - Map `InvalidTransitionException` → 422 with `INVALID_TRANSITION` code, include current and target status in message
    - Map `MethodArgumentNotValidException` → 400 with `VALIDATION_ERROR` code, include all field violations in message
    - Map `HttpMessageNotReadableException` → 400 with `MALFORMED_JSON` code
    - Map `LlmException` → 500 with `LLM_ERROR` code, generic message — no stack trace
    - Map `Exception` (catch-all) → 500 with `INTERNAL_ERROR` code, generic message — no stack trace
    - Build `ErrorResponse` record with `error`, `message`, `Instant.now()` timestamp, and request URI path
    - _Requirements: 8.1–8.5_

  - [x]* 4.3 Write property test for error envelope completeness (Property 9)
    - **Property 9: Error envelope completeness and no stack trace leakage**
    - Use jqwik `@Property` to generate exceptions of each mapped type via `@ForAll @From` arbitraries
    - Invoke `GlobalExceptionHandler` via `@WebMvcTest` slice with `MockMvc`
    - Assert response body contains non-null, non-blank `error`, `message`, `timestamp`, `path`
    - Assert response body does NOT contain `"at "` followed by a class name or `"Caused by:"`
    - **Validates: Requirements 8.1, 8.3, 8.4**

- [x] 5. Ticket state machine
  - [x] 5.1 Implement `TicketStateMachine`
    - Create `com.example.kirotest.service.TicketStateMachine` as a pure Java class (no Spring annotations)
    - Define `VALID_TRANSITIONS` as a static `Map<TicketStatus, Set<TicketStatus>>` with all five valid transitions from design
    - Implement `transition(TicketStatus current, TicketStatus target)`: return `target` if valid, throw `InvalidTransitionException` if not
    - Add Javadoc explaining the adjacency map design decision
    - _Requirements: 5.1–5.7_

  - [x]* 5.2 Write property tests for state machine transitions (Properties 1 and 2)
    - **Property 1: State machine rejects all invalid transitions**
    - **Property 2: State machine accepts all valid transitions**
    - Create `TicketStateMachineProperties` using jqwik `@Property(tries = 100)`
    - Generate all `(TicketStatus from, TicketStatus to)` pairs using `@ForAll TicketStatus` arbitraries
    - Assert `transition(from, to)` throws `InvalidTransitionException` iff `to` is NOT in the valid-next set for `from`
    - Assert `transition(from, to)` returns `to` when the transition IS valid
    - Instantiate `TicketStateMachine` directly — no Spring context
    - **Validates: Requirements 5.1–5.7**

- [x] 6. Service layer — TicketService and CommentService
  - [x] 6.1 Implement `TicketServiceImpl`
    - Create `TicketService` interface and `TicketServiceImpl` with `@Service @Slf4j @RequiredArgsConstructor`
    - Inject `TicketRepository`, `TicketStateMachine`, `ApplicationEventPublisher` via constructor
    - `createTicket`: fetch next sequence value, build `"TKT-" + value` ID, set status `OPEN`, persist, publish `TicketCreatedEvent`, return `TicketResponse`
    - `listTickets`: delegate to correct repository method based on presence of `status` and `search` params, wrap result in `PagedResponse`
    - `getTicket`: find by ID or throw `TicketNotFoundException`; map to `TicketResponse` with comments ordered by `createdAt ASC`
    - `updateTicket`: patch only non-null fields, re-persist, publish `TicketUpdatedEvent`, return updated `TicketResponse`; throw `TicketNotFoundException` if not found
    - `updateStatus`: delegate to `TicketStateMachine.transition()`, persist new status (and resolutionNotes if present), publish `TicketUpdatedEvent`, return updated `TicketResponse`
    - Never expose JPA entities — always map to DTOs before returning
    - _Requirements: 1.1, 1.2, 2.1–2.5, 3.1–3.3, 4.1–4.5, 5.1–5.11_

  - [x] 6.2 Implement `CommentServiceImpl`
    - Create `CommentService` interface and `CommentServiceImpl` with `@Service @Slf4j @RequiredArgsConstructor`
    - Inject `CommentRepository`, `TicketRepository`, `ApplicationEventPublisher` via constructor
    - `addComment`: load ticket (throw `TicketNotFoundException` if absent), persist comment, touch `ticket.updatedAt` via a no-op save to trigger `@LastModifiedDate`, publish `CommentAddedEvent`, return `CommentResponse` with HTTP 201
    - _Requirements: 6.1–6.5_

  - [x]* 6.3 Write property test for partial update field preservation (Property 10)
    - **Property 10: Partial update preserves unmodified fields**
    - Use jqwik to generate random `Ticket` instances (persisted to H2 via `@SpringBootTest` with `test` profile) and random non-empty subsets of `{title, description, priority, assignee}`
    - Apply `updateTicket` with only the generated subset; assert each supplied field equals the payload value
    - Assert each absent field retains its original value; assert `updatedAt >= original updatedAt`
    - **Validates: Requirements 4.1, 4.5**

- [x] 7. Checkpoint — core domain compiles and state machine tests pass
  - Ensure all classes compile with `./gradlew compileJava`
  - Ensure `TicketStateMachineProperties` property tests pass with `./gradlew test`
  - Ask the user if any questions arise before proceeding to the embedding layer.

- [x] 8. EmbeddingService and domain events
  - [x] 8.1 Implement `EmbeddingServiceImpl`
    - Create `EmbeddingService` interface with `ingestTicket(Ticket)`, `reingestTicket(Ticket)`, `ingestComment(Comment)`, `deleteChunks(String ticketId)` methods
    - Create `EmbeddingServiceImpl` with `@Service @Slf4j @RequiredArgsConstructor`; inject `EmbeddingModel`, `VectorStore`, `AppAiProperties` via constructor
    - `ingestTicket`: build DESCRIPTION prose chunk `"[title]\n\n[description]"`, construct metadata map (ticketId, chunkType=DESCRIPTION, status, priority, assignee, createdAt), create Spring AI `Document`, call `vectorStore.add()`; catch all exceptions, log at `ERROR`, do NOT rethrow
    - `reingestTicket`: call `deleteChunks(ticketId)`, then `ingestTicket`; if status is RESOLVED or CLOSED and resolutionNotes present, also add RESOLUTION chunk `"Resolution: [resolutionNotes]"`; catch/log, do NOT rethrow
    - `ingestComment`: build COMMENT prose `"Comment by [author]: [body]"`, metadata (ticketId, chunkType=COMMENT, status, priority, assignee, createdAt), add to vector store; catch/log at `WARN`, do NOT rethrow
    - `deleteChunks`: call `vectorStore.delete(filter by ticketId metadata)`; if deletion fails, log `WARN` (stale chunks) and continue
    - _Requirements: 1.7, 1.8, 4.4, 5.9, 5.11, 6.4, 9.1–9.7_

  - [x] 8.2 Wire async ApplicationEvent listeners in `EmbeddingServiceImpl`
    - Add `@EventListener` + `@Async` methods for `TicketCreatedEvent` → `ingestTicket`, `TicketUpdatedEvent` → `reingestTicket`, `CommentAddedEvent` → `ingestComment`
    - Add `@EnableAsync` to the main application configuration class
    - Ensure async failures are caught and logged — they must NOT propagate to the HTTP response thread
    - _Requirements: 1.8, 4.4, 5.11, 6.4_

  - [ ]* 8.3 Write property test for chunk metadata completeness (Property 8)
    - **Property 8: Chunk metadata completeness and correctness**
    - Use jqwik to generate arbitrary `Ticket` instances (valid field values)
    - Call the chunk-building logic inside `EmbeddingServiceImpl` directly (extract to package-private helper or test via mock VectorStore capture)
    - Assert the resulting `Document` metadata contains non-null `ticketId`, `chunkType`, `status`, `priority`, `createdAt`
    - Assert `ticketId` equals `ticket.getId()`, `chunkType` matches the expected type for each call path
    - **Validates: Requirements 1.7, 9.1**

- [x] 9. RagService and AI Ask endpoint
  - [x] 9.1 Implement `RagServiceImpl`
    - Create `RagService` interface with `AskResponse ask(AskRequest request)`
    - Create `RagServiceImpl` with `@Service @Slf4j @RequiredArgsConstructor`; inject `EmbeddingModel`, `VectorStore`, `ChatModel`, `AppAiProperties` via constructor
    - Build `SearchRequest` using `topK` and `similarityThreshold` from `AppAiProperties`
    - **Guardrail**: if `chunks.isEmpty()` after similarity search, return `AskResponse("No relevant tickets were found to answer this question.", List.of(), false)` immediately — do NOT call `ChatModel`
    - Build system prompt from grounding template (as per design), substituting `{context}` (chunks joined with `\n---\n`, each prefixed with ticketId) and `{question}`
    - Call `ChatModel.call(new Prompt(messages, ChatOptionsBuilder.builder().withTemperature(0.0f).build()))`; wrap any exception as `LlmException`
    - Extract `ticketId` values from `Document.getMetadata()` of retrieved chunks for `sources`
    - Log first 200 chars of question and retrieved chunk IDs at `INFO`
    - Return `AskResponse(answer, sources, true)`
    - _Requirements: 10.1–10.9_

  - [x]* 9.2 Write property test for RAG no-match guardrail (Property 6)
    - **Property 6: RAG no-match guardrail is deterministic**
    - Use jqwik to generate arbitrary question strings
    - Mock `VectorStore` to always return an empty list
    - Call `RagServiceImpl.ask()` for each generated question
    - Assert `ChatModel.call()` is NEVER invoked (verify with Mockito `verifyNoInteractions`)
    - Assert response has `grounded == false`, `sources.isEmpty()`, and `answer` equals the exact no-match phrase
    - **Validates: Requirements 10.3**

  - [x]* 9.3 Write property test for LLM call parameters grounding (Property 7)
    - **Property 7: LLM call parameters are always grounded**
    - Use jqwik to generate arbitrary question strings
    - Mock `VectorStore` to return non-empty chunk list; capture the `Prompt` argument passed to `ChatModel.call()` via Mockito `ArgumentCaptor`
    - Assert the captured `Prompt` contains a `SystemMessage` with the grounding instruction text
    - Assert the `ChatOptions` temperature equals `0.0f`
    - **Validates: Requirements 10.4, 10.5**

- [x] 10. Controller layer — TicketController, CommentController, AiController
  - [x] 10.1 Implement `TicketController`
    - Create `com.example.kirotest.controller.TicketController` with `@RestController @RequestMapping("/api/v1/tickets") @RequiredArgsConstructor`
    - `POST /` → `createTicket(@Valid @RequestBody)` → 201 with `Location` header and `TicketResponse` body
    - `GET /` → `listTickets(@RequestParam Optional<TicketStatus> status, @RequestParam Optional<String> search, Pageable pageable)` → 200 `PagedResponse<TicketSummaryResponse>`
    - `GET /{id}` → `getTicket(@PathVariable String id)` → 200 `TicketResponse`
    - `PATCH /{id}` → `updateTicket(@PathVariable, @Valid @RequestBody UpdateTicketRequest)` → 200 `TicketResponse`
    - `PATCH /{id}/status` → `updateStatus(@PathVariable, @Valid @RequestBody UpdateStatusRequest)` → 200 `TicketResponse`
    - Add `@Operation`, `@ApiResponse` SpringDoc annotations on each method
    - _Requirements: 1.1–1.6, 2.1–2.7, 3.1–3.3, 4.1–4.5, 5.1–5.11_

  - [x] 10.2 Implement `CommentController` and `AiController`
    - Create `CommentController` with `@RestController @RequestMapping("/api/v1/tickets/{id}/comments")`; `POST /` → 201 with `Location` header and `CommentResponse` body
    - Create `AiController` with `@RestController @RequestMapping("/api/v1/ai")`; `POST /ask` → `@Valid @RequestBody AskRequest` → 200 `AskResponse`
    - Add SpringDoc annotations
    - _Requirements: 6.1–6.5, 10.1–10.9_

- [x] 11. Unit and slice tests
  - [x] 11.1 Write `TicketServiceImplTest` unit tests
    - Mock `TicketRepository`, `TicketStateMachine`, `ApplicationEventPublisher` with Mockito `@Mock`/`@InjectMocks`
    - Test `createTicket`: verify ID format `TKT-{seq}`, status set to `OPEN`, `save()` called, `TicketCreatedEvent` published
    - Test `getTicket`: verify `TicketNotFoundException` thrown for unknown ID
    - Test `updateStatus`: verify delegation to `TicketStateMachine` and event publication; verify `InvalidTransitionException` propagated
    - Test `listTickets`: verify correct repository method called for each filter combination (no filter, status only, keyword only, both)
    - Use AssertJ for all assertions; follow AAA pattern; name tests `methodName_stateUnderTest_expectedBehavior`
    - _Requirements: 1.1, 2.1–2.5, 3.2, 4.1, 5.1–5.7_

  - [x] 11.2 Write `CommentServiceImplTest` unit tests
    - Mock `CommentRepository`, `TicketRepository`, `ApplicationEventPublisher`
    - Test `addComment` success: comment persisted, `CommentAddedEvent` published, `updatedAt` touched
    - Test `addComment` with non-existent ticket: `TicketNotFoundException` thrown
    - _Requirements: 6.1–6.5_

  - [x] 11.3 Write `EmbeddingServiceImplTest` unit tests
    - Mock `EmbeddingModel`, `VectorStore`
    - Test `ingestTicket`: verify correct prose chunk text, correct metadata keys/values, `vectorStore.add()` called once
    - Test `ingestTicket` with embedding failure: exception caught, `ERROR` logged, no exception propagated
    - Test `reingestTicket`: `deleteChunks` called before `ingestTicket`
    - Test `ingestComment`: COMMENT chunk text `"Comment by {author}: {body}"`, `WARN` logged on failure
    - _Requirements: 1.7, 1.8, 4.4, 5.9, 9.1–9.4_

  - [x] 11.4 Write `RagServiceImplTest` unit tests
    - Mock `EmbeddingModel`, `VectorStore`, `ChatModel`
    - Test guardrail: empty chunks → no `ChatModel` call, `grounded=false`, exact no-match phrase returned
    - Test happy path: non-empty chunks → `ChatModel` called with temperature `0.0f`, `grounded=true`, `sources` list populated from metadata
    - Test `LlmException` thrown when `ChatModel` call fails
    - _Requirements: 10.1–10.9_

  - [x]* 11.5 Write `@WebMvcTest` slice tests for all controllers
    - `TicketControllerTest`: cover `POST /api/v1/tickets` (201, 400 for blank title, 400 for missing priority), `GET /api/v1/tickets` (200 with pagination envelope), `GET /api/v1/tickets/{id}` (200, 404), `PATCH /{id}` (200, 400, 404), `PATCH /{id}/status` (200, 422)
    - `CommentControllerTest`: cover `POST /api/v1/tickets/{id}/comments` (201, 400 for blank body, 404)
    - `AiControllerTest`: cover `POST /api/v1/ai/ask` (200, 400 for blank question, 400 for oversized question, 500 on LLM error)
    - Mock all service layer beans with `@MockBean`; use `MockMvc` + `@AutoConfigureMockMvc`
    - Verify error envelope structure (`error`, `message`, `timestamp`, `path`) on all error responses
    - _Requirements: 1.1–1.6, 3.1–3.3, 6.1–6.3, 8.1–8.5, 10.1–10.9_

  - [x]* 11.6 Write property tests for whitespace input validation (Property 3)
    - **Property 3: Whitespace-only inputs are always rejected**
    - Use jqwik to generate whitespace-only strings: spaces, tabs, newlines, combinations, any length ≥ 1
    - Test each required field: `title` in `CreateTicketRequest`, `description` in `CreateTicketRequest`, `question` in `AskRequest`, `body` in `AddCommentRequest`
    - Via `@WebMvcTest` MockMvc, assert HTTP 400 and the violated field name appears in the error envelope `message`
    - **Validates: Requirements 1.3, 1.4, 6.2, 10.6**

  - [x]* 11.7 Write property tests for keyword search correctness (Property 4)
    - **Property 4: Keyword search is a correct filter**
    - Use jqwik to generate a random list of ticket titles/descriptions and a random keyword
    - Persist tickets to H2 (via `@DataJpaTest` with `test` profile)
    - Execute `TicketRepository.searchByKeyword(keyword, pageable)`
    - Assert every returned ticket contains the keyword (case-insensitive) in title or description
    - Assert no ticket containing the keyword is absent from the result (no false negatives)
    - **Validates: Requirements 2.3, 7.1**

  - [x]* 11.8 Write property tests for combined filter conjunctiveness (Property 5)
    - **Property 5: Combined status and keyword filter is conjunctive**
    - Use jqwik to generate random `(status, keyword)` pairs and random ticket sets with mixed statuses/content
    - Execute `TicketRepository.searchByKeywordAndStatus(keyword, status, pageable)` against H2
    - Assert all returned tickets satisfy BOTH the status match AND keyword match conditions
    - Assert tickets failing either condition are not present in results
    - **Validates: Requirements 2.4, 7.4**

- [x] 12. Checkpoint — all unit and property tests pass
  - Run `./gradlew test` — all unit, slice, and property-based tests must pass
  - Run JaCoCo report: verify state machine branch coverage is 100%, service layer ≥ 90%
  - Ask the user if any questions arise before proceeding to integration tests.

- [x] 13. Integration tests with Testcontainers
  - [x] 13.1 Create `TestFixtures` factory class and Testcontainers base configuration
    - Create `com.example.kirotest.TestFixtures` with static factory methods: `aTicket()`, `aComment(Ticket)`, `aCreateTicketRequest()`, `anAskRequest()`
    - Create abstract `AbstractIntegrationTest` base class: declare `@Container PostgreSQLContainer` with `pgvector/pgvector` image, set Spring datasource properties via `@DynamicPropertySource`
    - Create `application-integrationtest.yml` profile with `ddl-auto: create-drop`, Flyway enabled pointing to Testcontainers, Spring AI simple vector store or mock embedding for non-RAG tests
    - _Requirements: 15.2_

  - [x] 13.2 Write ticket lifecycle integration tests
    - Test full HTTP → Controller → Service → Repository → DB slice using `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
    - Test create ticket: POST → 201, DB row exists, `createdAt`/`updatedAt` populated via auditing
    - Test list tickets with status filter: create tickets in multiple statuses, GET with `?status=OPEN`, assert only OPEN tickets returned
    - Test list tickets with keyword search: create tickets with distinct keywords, assert correct filtering
    - Test get ticket detail with comments: create ticket, add comments, GET → comments ordered by `createdAt ASC`
    - Test state machine transitions end-to-end: OPEN → IN_PROGRESS → RESOLVED → CLOSED (HTTP 200 each); attempt CLOSED → OPEN (HTTP 422)
    - Test partial update: PATCH with subset of fields, assert only modified fields changed, `updatedAt` advanced
    - _Requirements: 1.1–1.2, 2.1–2.5, 3.1, 4.1–4.5, 5.1–5.7, 6.1_

  - [x] 13.3 Write RAG pipeline integration tests
    - Use Testcontainers with real PGVector; wire real `EmbeddingModel` (Ollama or mocked provider via `test` profile)
    - Retrieval smoke test: ingest a known ticket, query with a semantically similar question, assert the ticket's `ticketId` appears in `sources`
    - No-match test: clear vector store, ask a question, assert `grounded=false` and exact no-match phrase
    - Grounding test: ingest 2+ tickets, ask a focused question, assert `grounded=true` and `sources` contains at least one real `ticketId`
    - Threshold test: configure `similarity-threshold=0.99`, assert fewer results than with `similarity-threshold=0.5`
    - _Requirements: 10.1–10.3, 10.8_

- [x] 14. Frontend — Next.js project setup
  - [x] 14.1 Initialise Next.js app and install dependencies
    - Scaffold `frontend/` directory with `create-next-app --typescript`; configure `next.config.ts` with API proxy rewrites to `http://localhost:8080`
    - Install dependencies: `swr` or `@tanstack/react-query` for data fetching, `axios` or native `fetch`, a UI library (e.g. shadcn/ui or Tailwind CSS)
    - Create `src/lib/types.ts` mirroring all backend response DTOs: `TicketSummaryResponse`, `TicketResponse`, `CommentResponse`, `PagedResponse<T>`, `AskResponse`, `ErrorResponse`
    - _Requirements: 12.1, 13.1, 14.1_

  - [x] 14.2 Create API client layer
    - Create `src/lib/api/ticketsApi.ts`: typed functions for `listTickets(params)`, `getTicket(id)`, `createTicket(req)`, `updateTicket(id, req)`, `updateStatus(id, req)` — each returning the appropriate typed DTO
    - Create `src/lib/api/commentsApi.ts`: `addComment(ticketId, req)`
    - Create `src/lib/api/aiApi.ts`: `ask(req)`
    - All functions must extract `message` from `ErrorResponse` on non-2xx and rethrow as a typed error
    - _Requirements: 12.4, 13.6, 14.4_

  - [x] 14.3 Implement `ErrorNotification`, `LoadingSpinner`, and `Pagination` common components
    - `ErrorNotification.tsx`: accepts `message: string`, renders a visible error notification (e.g. banner or toast)
    - `LoadingSpinner.tsx`: simple spinner shown during pending API calls
    - `Pagination.tsx`: accepts `page`, `totalPages`, `onPageChange`; renders prev/next controls and page indicator
    - _Requirements: 12.4, 12.5, 12.6_

- [x] 15. Frontend — Ticket list page
  - [x] 15.1 Implement `TicketSearchBar`, `TicketTable`, and `TicketListPage`
    - `TicketSearchBar.tsx`: text input for keyword search + status dropdown (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`); fires `onSearch(keyword, status)` callback on submit
    - `TicketTable.tsx`: renders table/card layout with columns `id`, `title`, `status`, `priority`, `assignee`, `createdAt`; renders "No tickets found" when `data` array is empty; each row links to ticket detail page
    - `src/app/tickets/page.tsx` (`TicketListPage`): uses SWR/React Query hook to call `listTickets`, passes `?search` and `?status` params, renders `TicketSearchBar` + `TicketTable` + `Pagination`; displays `ErrorNotification` on API error
    - _Requirements: 12.1–12.6_

- [x] 16. Frontend — Ticket detail page
  - [x] 16.1 Implement `StatusTransitionControl`, `CommentThread`, `AddCommentForm`, and `TicketDetailCard`
    - `StatusTransitionControl.tsx`: dropdown of valid next statuses + confirm button; calls `updateStatus` API; on HTTP 200 refreshes ticket data optimistically; on HTTP 422 shows `ErrorNotification` with envelope `message`
    - `CommentThread.tsx`: renders ordered list of comments (oldest-first) each with `author`, `body`, `createdAt`
    - `AddCommentForm.tsx`: `body` and `author` inputs; on submit calls `addComment` API; on HTTP 201 appends new comment without full reload; on error shows `ErrorNotification`
    - `TicketDetailCard.tsx`: displays all ticket fields; inline edit mode for `title`, `description`, `priority`, `assignee`; on save calls `updateTicket` with only modified fields; on HTTP 200 updates displayed values; on error shows `ErrorNotification` and preserves unsaved input
    - _Requirements: 13.1–13.6_

  - [x] 16.2 Implement `TicketDetailPage`
    - Create `src/app/tickets/[id]/page.tsx`: fetch ticket via `useTicket(id)` hook; render `TicketDetailCard`, `StatusTransitionControl`, `CommentThread`, `AddCommentForm`; show `LoadingSpinner` during fetch; show `ErrorNotification` on any API error
    - _Requirements: 13.1–13.6_

- [x] 17. Frontend — AI Ask panel
  - [x] 17.1 Implement `AiAskPanel`, `SourceLinks`, and `AIAskPage`
    - `AiAskPanel.tsx`: question textarea + submit button; client-side validation `question.trim().length > 0` with inline "Question cannot be empty" message; shows `LoadingSpinner` while request pending; on `grounded: true` renders answer text + `SourceLinks`; on `grounded: false` renders answer text only; on API error shows `ErrorNotification` with envelope message
    - `SourceLinks.tsx`: renders each `ticketId` in `sources` as a `<Link href={/tickets/${id}}>{id}</Link>` chip
    - Create `src/app/ai/page.tsx`: renders `AiAskPanel`
    - _Requirements: 14.1–14.5_

- [x] 18. Final checkpoint — full system integration
  - Run `./gradlew test` — all unit, slice, and property tests must pass
  - Run `./gradlew integrationTest` — all Testcontainers integration tests must pass (requires Docker)
  - Start backend with `./gradlew bootRun --args='--spring.profiles.active=dev'` and verify Swagger UI loads at `http://localhost:8080/swagger-ui.html`
  - Start frontend with `npm run dev` inside `frontend/` and verify ticket list page loads
  - Ask the user if any questions arise before final documentation tasks.

- [x] 19. Documentation
  - [x] 19.1 Add Javadoc to all public classes and public methods
    - Every public class in `controller`, `service`, `repository`, `domain`, `exception`, `config` packages must have a class-level Javadoc describing purpose and design intent
    - Every public method must have a Javadoc describing what and why (not just restating the signature)
    - Complex logic (state machine adjacency map, RAG guardrail, chunk text construction) must have inline comments
    - _Requirements: documentation guidelines_

  - [x] 19.2 Create `README.md` with full setup instructions
    - Project overview paragraph
    - Prerequisites: Java 21, Docker, Ollama (dev) or OpenAI API key (prod)
    - Local setup: step-by-step clone → configure env vars → run migrations → start backend → start frontend
    - Test commands: `./gradlew test` (unit + slice), `./gradlew integrationTest` (Testcontainers — requires Docker)
    - Required environment variables (names only): `OPENAI_API_KEY`, `DB_PASSWORD`, `DB_URL`, `CORS_ALLOWED_ORIGINS`
    - Links to spec documents in `.kiro/specs/support-ticket-management/`
    - Document Docker requirement for Testcontainers in `CONTRIBUTING.md`
    - _Requirements: 15.3, documentation guidelines_

---

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP; they are property-based or unit/integration tests
- Each task references specific requirements from `requirements.md` for traceability
- Checkpoints (tasks 7, 12, 18) are synchronisation points — do not skip them
- Property tests use jqwik and must be placed in `src/test/java` alongside unit tests
- Integration tests requiring Testcontainers (Docker) are run via `./gradlew integrationTest` as a separate Gradle task
- The state machine branch coverage must be 100% (enforced by JaCoCo) — this is non-negotiable per testing guidelines
- All secrets must be supplied via environment variables; any violation causes a startup failure by design
- Frontend lives in a `frontend/` subdirectory; the backend API proxy is configured in `next.config.ts`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "1.4", "2.1", "2.2"] },
    { "id": 2, "tasks": ["2.3", "3.1", "3.2", "4.1"] },
    { "id": 3, "tasks": ["4.2", "5.1"] },
    { "id": 4, "tasks": ["4.3", "5.2", "6.1"] },
    { "id": 5, "tasks": ["6.2", "6.3"] },
    { "id": 6, "tasks": ["8.1"] },
    { "id": 7, "tasks": ["8.2", "8.3"] },
    { "id": 8, "tasks": ["9.1", "11.1", "11.2", "11.3"] },
    { "id": 9, "tasks": ["9.2", "9.3", "10.1", "11.4", "11.6", "11.7", "11.8"] },
    { "id": 10, "tasks": ["10.2", "11.5"] },
    { "id": 11, "tasks": ["13.1"] },
    { "id": 12, "tasks": ["13.2", "13.3"] },
    { "id": 13, "tasks": ["14.1"] },
    { "id": 14, "tasks": ["14.2", "14.3"] },
    { "id": 15, "tasks": ["15.1"] },
    { "id": 16, "tasks": ["16.1"] },
    { "id": 17, "tasks": ["16.2", "17.1"] },
    { "id": 18, "tasks": ["19.1", "19.2"] }
  ]
}
```
