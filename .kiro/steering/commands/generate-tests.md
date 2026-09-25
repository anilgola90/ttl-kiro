---
inclusion: manual
---

# Command: Generate Tests

Use this command to generate tests for a given class, method, or feature. Always verify AI-generated tests before committing — see the hallucination guard at the bottom.

## How to invoke
Reference the source file you want tests for, then ask:
> "Generate tests for this class using the generate-tests command"

## What to Generate

### For Service Classes
- Unit tests with Mockito mocks for all collaborators
- One test per method, one test per edge case
- Parameterised tests for state machine transitions (use `@ParameterizedTest` + `@MethodSource`)

### For Controllers
- `@WebMvcTest` slice tests using MockMvc
- Test: happy path, 400 validation failure, 404 not found, 422 invalid transition
- Assert both HTTP status AND response body shape

### For State Machine (Mandatory Coverage)
Generate a parameterised test covering every transition:
```java
@ParameterizedTest
@MethodSource("validTransitions")
void transition_validTransition_succeeds(TicketStatus from, TicketStatus to) { ... }

@ParameterizedTest
@MethodSource("invalidTransitions")
void transition_invalidTransition_throws422(TicketStatus from, TicketStatus to) { ... }
```
Valid: OPEN→IN_PROGRESS, IN_PROGRESS→RESOLVED, RESOLVED→CLOSED, OPEN→CANCELLED, IN_PROGRESS→CANCELLED
Invalid: CLOSED→OPEN, RESOLVED→OPEN, CANCELLED→OPEN, CLOSED→IN_PROGRESS, RESOLVED→IN_PROGRESS

### For RAG / AI Layer
- Retrieval smoke test: ingest a known ticket, assert it appears in top-K results for a relevant query
- No-match test: assert response body contains "No relevant tickets were found" when nothing matches
- Grounding test: assert `sources` list in response is non-empty for a matched query
- Threshold test: assert reducing `similarity-threshold` returns more results

### For Repository Layer
- Integration tests with `@DataJpaTest` + H2 (unit) or Testcontainers PostgreSQL (integration)
- Test custom query methods: search by keyword, filter by status, pagination

## Test Naming Convention
```
methodName_stateUnderTest_expectedBehavior
```
Examples:
- `createTicket_withValidInput_returns201`
- `transitionStatus_fromClosedToOpen_throws422`
- `askQuestion_withNoMatchingTickets_returnsNoRelevantMessage`

## Test Structure (AAA)
```java
@Test
void methodName_stateUnderTest_expectedBehavior() {
    // Arrange
    ...
    // Act
    ...
    // Assert
    ...
}
```

## Hallucination Guard — Verify Before Committing
After AI generates tests, check every single one:
- [ ] All imports resolve to real classes on the classpath
- [ ] Class names match the actual source files exactly
- [ ] Method names called in tests exist in the actual implementation
- [ ] Assertion field names match actual DTO/record field names
- [ ] No invented Spring annotations or test utilities
- [ ] `@MockBean` / `@Mock` used consistently — no mixed usage
- [ ] `@SpringBootTest` not used for pure unit tests (overkill)

Flag any invented methods or classes in `docs/ai-mistakes.md`.
