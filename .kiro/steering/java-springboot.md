# Java Spring Boot Guidelines

## Project Baseline
- Java 21 (use records, sealed classes, pattern matching, virtual threads where appropriate)
- Spring Boot 3.x (not 2.x — do NOT use deprecated `WebSecurityConfigurerAdapter`, use `SecurityFilterChain` beans)
- Build tool: Gradle (Kotlin DSL preferred for new projects; Groovy DSL acceptable for existing)
- Package structure: `com.example.<app>.<layer>` — e.g. `controller`, `service`, `repository`, `domain`, `dto`, `exception`, `config`

## Code Style
- Use Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`) to reduce boilerplate
- Prefer constructor injection over field injection (`@Autowired` on fields is banned)
- All service interfaces must have a single implementation class suffixed `Impl` (e.g. `TicketServiceImpl`)
- Use `record` types for immutable DTOs and value objects
- Never expose JPA entities directly in API responses — always map to a DTO
- Use `Optional` for nullable return values from repositories; do NOT return `null`

## Layering Rules
- Controllers: HTTP concerns only — no business logic
- Services: all business logic, state machine transitions, validation logic
- Repositories: Spring Data JPA only — no native SQL unless absolutely necessary and documented
- Domain/entity classes: annotated with `@Entity`, never used as request/response bodies

## Exception Handling
- Define a `GlobalExceptionHandler` using `@RestControllerAdvice`
- Map domain exceptions to HTTP status codes explicitly (e.g. `TicketNotFoundException` → 404, `InvalidTransitionException` → 422)
- Never expose stack traces in API responses
- Return a consistent error envelope: `{ "error": "...", "message": "...", "timestamp": "..." }`

## Validation
- Use Bean Validation (`@NotBlank`, `@NotNull`, `@Size`, `@Valid`) on all request DTOs
- Validate at the controller boundary (`@Valid` on `@RequestBody`)
- Business-rule validation (e.g. state machine) lives in the service layer

## Configuration
- All tuneable values (top-K, similarity threshold, embedding model, timeouts) must be in `application.properties` / `application.yml` and bound via `@ConfigurationProperties`
- Never hardcode environment-specific values in source code
- Use profiles: `dev`, `test`, `prod`

## Database
- Use Flyway or Liquibase for schema migrations — never `spring.jpa.hibernate.ddl-auto=create` in production
- `ddl-auto=validate` in prod, `ddl-auto=create-drop` in test only
- All entities must have `@CreatedDate` / `@LastModifiedDate` via Spring Data Auditing

## Security
- No secrets in source code or committed config files
- Use environment variables or AWS Secrets Manager / Vault for credentials
- `.env` files must be in `.gitignore`

## Logging
- Use SLF4J + Logback (via `@Slf4j`)
- Log at `INFO` for business events, `DEBUG` for internal state, `WARN`/`ERROR` for exceptions
- Never log sensitive data (PII, tokens, passwords)
