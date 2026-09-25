# Testing Guidelines

## Framework Stack
- Unit tests: JUnit 5 + Mockito
- Integration tests: `@SpringBootTest` + Testcontainers (PostgreSQL, PGVector)
- API/slice tests: `@WebMvcTest` + MockMvc
- Property-based tests: jqwik (for state machine transitions, input validation)
- Assertions: AssertJ (preferred over plain JUnit assertions)

## Test Structure
Follow AAA (Arrange / Act / Assert) in every test method.
Name tests using: `methodName_stateUnderTest_expectedBehavior`
Example: `transitionStatus_fromOpenToInProgress_succeeds`

## Coverage Targets
- Service layer: 90%+ line coverage
- State machine transitions: 100% — every valid and invalid transition must have a test
- Controllers: all endpoints covered via `@WebMvcTest`
- RAG / AI layer: covered via retrieval quality tests (see below)

## Unit Tests
- Mock all collaborators with `@MockBean` / `@Mock`
- Never spin up a Spring context for pure unit tests
- Test one behaviour per test method
- Use `@ParameterizedTest` for state machine transition tables

## Integration Tests
- Use Testcontainers for real PostgreSQL + PGVector — do NOT mock the database in integration tests
- Annotate with `@Testcontainers` and `@Container` for lifecycle management
- Use a dedicated `application-test.yml` profile — never share prod config
- Roll back transactions with `@Transactional` on test class where appropriate
- Test the full slice: HTTP → Controller → Service → Repository → DB

## State Machine Tests (Mandatory)
Test every valid transition:
- OPEN → IN_PROGRESS ✅
- IN_PROGRESS → RESOLVED ✅
- RESOLVED → CLOSED ✅
- OPEN → CANCELLED ✅
- IN_PROGRESS → CANCELLED ✅

Test every invalid transition (must return HTTP 422):
- CLOSED → OPEN ❌
- RESOLVED → OPEN ❌
- CANCELLED → OPEN ❌
- CLOSED → IN_PROGRESS ❌
- RESOLVED → IN_PROGRESS ❌

## RAG / Retrieval Quality Tests
- Retrieval smoke test: ingest a known ticket, query with a semantically similar question, assert the ticket appears in top-K results
- Grounding test: assert the LLM response cites at least one real `ticketId`
- No-match test: ask a question with no relevant tickets in the store, assert the response contains the "no relevant tickets found" phrase — NOT a fabricated answer
- Threshold test: vary `similarity-threshold` config and assert results change accordingly

## AI Output Validation (Hallucination Guard)
Before accepting any AI-generated test, verify:
1. Imports are real and on the classpath
2. Class/method names match the actual source
3. Assertions target the correct field/return type
4. No invented API methods that don't exist in Spring or the codebase

## Test Data
- Use factory methods or a `TestFixtures` class for reusable test objects
- Never share mutable test state between tests
- Use `@BeforeEach` for setup, not static initializers

## CI
- Tests must pass in CI (GitHub Actions / equivalent) before merge
- Testcontainers tests require Docker in CI — document this requirement in `CONTRIBUTING.md`
- Run `./gradlew test` for unit + integration; separate task `./gradlew integrationTest` for Testcontainers suites
