---
inclusion: manual
---

# Command: Review Code

Use this command to review a piece of code or an entire file for correctness, style, and security.

## How to invoke
Paste or reference the code you want reviewed, then ask:
> "Review this code using the review-code command"

## Review Checklist

### Correctness
- [ ] Does the logic match the stated requirement or spec?
- [ ] Are edge cases handled (null inputs, empty collections, zero values, boundary conditions)?
- [ ] Are all exception paths caught and mapped to the correct HTTP status?
- [ ] Does the state machine enforcement match `spec/state-machine.md` exactly?

### Spring Boot / Java 21 Standards (see java-springboot.md)
- [ ] No field injection (`@Autowired` on fields) — constructor injection only
- [ ] Entities are never returned directly from controllers — DTOs used
- [ ] `Optional` used for nullable repository returns, not null checks
- [ ] `@Valid` present on `@RequestBody` parameters
- [ ] No hardcoded config values — everything in `application.yml`

### Security
- [ ] No secrets, API keys, or passwords in source code
- [ ] No stack traces exposed in API responses
- [ ] SQL queries use parameterised statements (no string concatenation)
- [ ] Input validated before use

### Performance
- [ ] No N+1 queries (use `JOIN FETCH` or `@EntityGraph` where needed)
- [ ] Pagination used for list endpoints — no unbounded `findAll()`
- [ ] No synchronous blocking calls inside reactive chains (if applicable)

### Logging
- [ ] No sensitive data logged (PII, tokens, passwords)
- [ ] Appropriate log level used (INFO for business events, DEBUG for internals)

### Tests
- [ ] Is there a corresponding unit test for this logic?
- [ ] Are all new branches covered?
- [ ] Do test names follow `methodName_stateUnderTest_expectedBehavior`?

## Output Format
Respond with:
1. **Issues Found** — list each problem with file/line reference and severity (CRITICAL / WARNING / SUGGESTION)
2. **Corrected Snippet** — provide the fixed code inline
3. **Explanation** — one sentence per fix explaining why the original was wrong
