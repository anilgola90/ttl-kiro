# Contributing

Thanks for contributing to the AI-Powered Support Ticket Management System. This guide covers the test workflow and local requirements.

## Docker is required for integration tests

The integration test suite uses **Testcontainers** to start a real **PostgreSQL + PGVector** container (`pgvector/pgvector`). **Docker must be installed and running** before you run these tests — without a running Docker daemon the Testcontainers-backed tests will fail to start.

Unit tests, `@WebMvcTest` slice tests, and jqwik property tests run against an **H2** in-memory database and do **not** require Docker.

## Running the test suites

There are two Gradle test tasks:

```bash
# Unit + slice + property-based tests (H2 in-memory DB, no Docker needed)
./gradlew test

# Testcontainers integration tests (real PostgreSQL + PGVector) — requires Docker
./gradlew integrationTest
```

- Run `./gradlew test` for fast feedback during development.
- Run `./gradlew integrationTest` to exercise the full HTTP → Controller → Service → Repository → DB slice against a real database.
- In CI, Docker must be available for the integration tests to pass.

## Coverage expectations

- The `TicketStateMachine` must maintain **100% branch coverage** — every valid and invalid transition needs a test. This is enforced by JaCoCo (`./gradlew jacocoTestCoverageVerification`).
- The service layer targets **≥ 90% line coverage**.

## Test conventions

- Follow Arrange / Act / Assert in every test method.
- Name tests `methodName_stateUnderTest_expectedBehavior`.
- Prefer AssertJ assertions.
- Do not mock the database in integration tests — use Testcontainers.
- Do not commit secrets; all credentials are supplied via environment variables.
