# AI Mistakes Log

Record of meaningful mistakes, wrong suggestions, and hallucinated answers caught during development.

> **Requirement:** At least one code mistake and one RAG/hallucination mistake must be documented here before submission.

---

<!-- Template for each entry:

## Mistake N — [YYYY-MM-DD]
**Type:** Wrong code | Hallucinated answer | Bad test | Incorrect import | Ungrounded RAG answer

**What AI generated:**
```
paste the wrong snippet or answer here
```

**Why it was wrong:**
Your analysis of what's incorrect and why.

**What you changed:**
```
paste the corrected version here
```

**Lesson:** One-line takeaway to prevent recurrence.

---
-->
## Mistake 1 — 2026-09-24
**Type:** Wrong code — Spring Boot version-incompatible test API

**What AI generated:**
The design and initial integration tests used Spring Boot 3.x testing idioms:
```java
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;

@MockBean VectorStore vectorStore;
@Autowired TestRestTemplate restTemplate;
```

**Why it was wrong:**
The project is pinned to **Spring Boot 4.1.0-SNAPSHOT**. In Spring Boot 4:
- `@MockBean` (`org.springframework.boot.test.mock.mockito.MockBean`) was removed and replaced by `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`).
- `TestRestTemplate` was removed from `spring-boot-test` entirely. It is no longer on the default test classpath.

`gradle compileTestJava` failed with `package org.springframework.boot.test.mock.mockito does not exist` and `package org.springframework.boot.test.web.client does not exist`. The AI reproduced widely-documented Spring Boot 3 patterns from its training data without accounting for the 4.x baseline — a classic version-drift hallucination.

**What you changed:**
```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@MockitoBean VectorStore vectorStore;
@AutoConfigureMockMvc          // on the test class
@Autowired MockMvc mockMvc;    // drives HTTP via MockMvc instead of TestRestTemplate
```
Both integration tests were rewritten to use `MockMvc` + `ObjectMapper`, which is available in Boot 4 and is the approach the testing steering already prefers. `compileTestJava` and the test suite then passed.

**Lesson:** AI-generated framework code must be validated against the *actual* declared framework version, not the most common version in training data. Pin versions in the steering file and compile early.

---

## Mistake 2 — 2026-09-24
**Type:** Wrong code — typo introduced in constructor injection

**What AI generated:**
While editing `EmployeeController.java`, an assignment was produced as:
```java
this.eyesmployeeService = employeeService;
```

**Why it was wrong:**
`eyesmployeeService` is not a declared field — a garbled token that does not compile. It broke the entire main source compilation (`cannot find symbol`), which in turn blocked every test from running. This surfaced only when a property-test subagent tried to actually run `gradle test`.

**What you changed:**
```java
this.employeeService = employeeService;
```

**Lesson:** Trust-but-verify. A single-character/token hallucination in an otherwise-correct file can block the whole build. Always run a compile after AI edits rather than relying on the generated summary that claims success.

---

## Mistake 3 (RAG / grounding) — reserved
**Type:** Hallucinated / ungrounded RAG answer

**Note:** The grounding guardrail in `RagServiceImpl` was specifically designed to prevent this class of mistake: when `vectorStore.similaritySearch(...)` returns zero chunks above the similarity threshold, the service returns the fixed no-match phrase and **never calls the LLM** (verified by `RagServiceImplTest.ask_withNoMatchingChunks_returnsNoMatchWithoutCallingLlm` and `RagPipelineIntegrationTest`). During manual RAG testing, capture any instance where the assistant fabricated a ticket ID (e.g. cited `TKT-9999` that does not exist) or answered an out-of-scope question (e.g. "What is the capital of France?") with anything other than the no-match phrase, and record the exact question + response here. Use the `review-rag-output` command to evaluate.

## Mistake 1 — 2026-09-24
**Type:** Wrong code (incompatible dependency versions)

**What AI generated:**
In task 1.1, the AI scaffolded `build.gradle` pairing **Spring Boot 4.1.0-SNAPSHOT** (the version the project was initialized with) with **Spring AI 1.0.0** (the stable GA release):
```groovy
id 'org.springframework.boot' version '4.1.0-SNAPSHOT'
...
mavenBom 'org.springframework.ai:spring-ai-bom:1.0.0'
```

**Why it was wrong:**
Spring AI 1.0.0 was built and tested against Spring Boot 3.x. Its OpenAI/Ollama model
auto-configurations (`OpenAiChatAutoConfiguration`, `OllamaChatAutoConfiguration`, etc.)
`@Import` `org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration`,
which was moved/removed in Spring Boot 4. The result was a runtime
`ClassNotFoundException: RestClientAutoConfiguration` that only surfaced when the full
application context tried to load (i.e. `KirotestApplicationTests.contextLoads()`), NOT at
compile time. The AI presented the version pairing confidently as correct.

**What you changed:**
Downgraded to a Spring Boot version aligned with Spring AI 1.0.0's supported baseline:
```groovy
id 'org.springframework.boot' version '3.4.1'
```
and reverted the Spring Boot 4-specific test API imports the AI had subsequently used to
"paper over" the mismatch (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
→ `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`; removed the
`spring-boot-webmvc-test` module dependency).

**Lesson:** Pre-release framework versions (SNAPSHOT) rarely align with GA versions of
companion libraries. Always verify the compatibility matrix; a green compile does not mean
the runtime auto-configuration graph is compatible.

---

## Mistake 2 — 2026-09-24
**Type:** Wrong code (event listener on a proxied interface-backed bean)

**What AI generated:**
In task 8.2, the AI added the async re-ingestion `@EventListener` methods directly onto
`EmbeddingServiceImpl`, which implements the `EmbeddingService` interface:
```java
@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    @Async @EventListener
    public void onTicketCreated(TicketCreatedEvent event) { ingestTicket(event.ticket()); }
    ...
}
```

**Why it was wrong:**
Because the bean implements an interface and uses `@Async`, Spring wraps it in a **JDK
dynamic proxy** that only exposes the interface's methods. The `@EventListener` methods are
not on the interface, so Spring failed at startup with:
`Need to invoke method 'onTicketCreated' declared on target class 'EmbeddingServiceImpl',
but not found in any interface(s) of the exposed proxy type.` Like Mistake 1, this passed
compilation and only failed when the context loaded.

**What you changed:**
Extracted the listeners into a dedicated `TicketEmbeddingEventListener` `@Component` (a
concrete class with no implemented interface, so it is CGLIB-proxied and the listener
methods are visible), which delegates to `EmbeddingService`. This also improves separation
of concerns — event wiring is no longer mixed into the embedding logic.

**Lesson:** `@EventListener`/`@Scheduled` methods must live on a bean whose proxy exposes
them. For interface-backed `@Async`/`@Transactional` beans (JDK dynamic proxies), put such
methods on a separate concrete component or enforce `proxyTargetClass=true`.

---

## Mistake 3 — 2026-09-24
**Type:** Bad test (incorrect Mockito verification)

**What AI generated:**
In the `@WebMvcTest` slice test `TicketControllerTest.updateStatus_whenInvalidTransition_returns422`,
the AI stubbed `ticketService.updateStatus(...)` to throw, then asserted:
```java
Mockito.verifyNoMoreInteractions(ticketService);
```

**Why it was wrong:**
The test stubs `updateStatus` and the controller *does* call it, so that interaction is a
real, expected one. Calling `verifyNoMoreInteractions` without first `verify`-ing the
expected `updateStatus` call caused Mockito to report the (legitimate) stubbed invocation
as an unverified interaction → `NoInteractionsWanted` failure.

**What you changed:**
```java
Mockito.verify(ticketService).updateStatus(eq("TKT-1001"), any(UpdateStatusRequest.class));
Mockito.verifyNoMoreInteractions(ticketService);
```
Verify the expected interaction first, then assert there were no others.

**Lesson:** `verifyNoMoreInteractions` accounts for *all* interactions, including stubbed
calls that actually execute. Always `verify()` the expected calls before asserting "no more".

---

## Mistake 4 — 2026-09-24
**Type:** Wrong code (duplicate `@EnableJpaAuditing`)

**What AI generated:**
`@EnableJpaAuditing` was declared on BOTH the main `KirotestApplication` class (task 1.4)
AND a dedicated `JpaConfig` class (created in a later task), registering the
`jpaAuditingHandler` bean twice.

**Why it was wrong:**
Under Spring Boot 3.4 with the full test context, this produced
`BeanDefinitionOverrideException: Invalid bean definition with name 'jpaAuditingHandler' ...
since there is already [...] bound.`

**What you changed:**
Removed `@EnableJpaAuditing` from `KirotestApplication`, keeping it only in the dedicated
`JpaConfig` (the better location — it lets `@WebMvcTest` slices opt out of auditing).

**Lesson:** Enable cross-cutting features (`@EnableJpaAuditing`, `@EnableAsync`) in exactly
one place. Duplicated `@Enable*` annotations cause bean-override conflicts once a full
context assembles.

## Mistake 5 (RAG / ungrounded answer) — 2026-09-24
**Type:** Hallucinated / ungrounded RAG answer (misleading citation)

**How it was caught:** Live end-to-end testing against real Ollama (`nomic-embed-text` +
`llama3.2`) and PGVector. Three tickets were ingested; the assistant was then asked an
out-of-scope question.

**What the assistant produced:**
Question: `"What is the capital of France?"` (no relevant tickets exist)
Response:
```json
{"answer":"No relevant tickets were found to answer this question. \n\n(Ticket IDs: None - no ticket was provided...)",
 "sources":["TKT-1001"],
 "grounded":true}
```

**Why it was wrong:**
The original guardrail only checked *"did any chunk clear the similarity threshold?"* before
calling the LLM. With `nomic-embed-text` cosine scores, an unrelated ticket (TKT-1001) cleared
the (lowered) threshold by a thin, coincidental margin, so the LLM *was* invoked. The model
correctly said "no relevant tickets" in the answer text — but the code still returned
`grounded: true` with `sources: ["TKT-1001"]`. That is a **contradiction**: a cited, "grounded"
response whose own text admits nothing relevant was found. A caller trusting the `grounded`
flag / `sources` would surface a bogus citation.

**What you changed:**
Added a **post-generation guardrail** to `RagServiceImpl.ask(...)`: after the LLM responds, if
the answer text contains the canonical no-match phrase, normalise the response to
`AskResponse(NO_MATCH_PHRASE, List.of(), false)` — no sources, not grounded. Re-tested live:
the out-of-scope question now returns `grounded: false` with empty sources, while the in-scope
"payment failures" question still returns `grounded: true` citing TKT-1001/TKT-1002.

**Lesson:** "Retrieved a chunk above threshold" is NOT the same as "the answer is grounded."
Grounding must be validated on the *generated answer*, not just on retrieval. Always reconcile
the `grounded`/`sources` metadata with what the model actually said.

---

## Retrieval-tuning finding — 2026-09-24 (not a defect; documented per assignment)
**Observation:** With the default `app.ai.retrieval.similarity-threshold=0.75`, the in-scope
question "Have we seen payment failures before?" returned the no-match response even though
relevant tickets existed — retrieval returned zero chunks (`retrievedChunks=[]` in the logs).

**Cause:** The 0.75 default was chosen with OpenAI `text-embedding-3-small` in mind. Local
`nomic-embed-text` (768-dim) produces a different cosine-similarity distribution; relevant-but-
not-identical text commonly scores well below 0.75. Lowering the threshold to ~0.3 for the dev
(Ollama) profile produced correct grounded retrieval.

**Takeaway:** The similarity threshold is embedding-model-specific and MUST be tuned per model.
This is exactly why `top-k` and `similarity-threshold` are externalised as configuration
(Requirement 11) rather than hardcoded — the value was overridden at runtime via
`--app.ai.retrieval.similarity-threshold=0.3` with no code change. Recommend setting a lower
default in `application-dev.yml` for the Ollama profile.
