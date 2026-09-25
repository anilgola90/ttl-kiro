---
inclusion: manual
---

# Command: Review Spec

Use this command to review a spec document for completeness, consistency, and implementability before writing any code.

## How to invoke
Reference the spec file you want reviewed, then ask:
> "Review this spec using the review-spec command"

## Review Checklist

### Completeness
- [ ] All functional requirements from the assignment are addressed
- [ ] Non-functional requirements are present (performance, security, scalability)
- [ ] Every API endpoint has a defined request shape, response shape, and error cases
- [ ] State machine covers all valid transitions AND all explicitly invalid ones
- [ ] RAG pipeline covers: ingestion, chunking, embedding, retrieval, generation, grounding
- [ ] No-match / out-of-scope behaviour is explicitly defined

### Consistency
- [ ] Field names are consistent across all spec documents (e.g. `ticketId` vs `ticket_id`)
- [ ] Status enum values match exactly across state-machine.md, api-contract.md, data-model.md
- [ ] Endpoint paths in api-contract.md match rag-api-contract.md conventions
- [ ] Chunking strategy in rag-ingestion.md matches what architecture.md describes

### Implementability
- [ ] Each requirement is testable — has measurable acceptance criteria
- [ ] No ambiguous terms ("fast", "secure", "good") without measurable definition
- [ ] Technology choices are specified (Java 21, Spring Boot, PGVector, embedding model)
- [ ] Configuration values are named (top-K, similarity threshold) — not left as TBD

### RAG-Specific
- [ ] Chunking strategy is justified (why semantic over fixed-size)
- [ ] Embedding model choice is documented with cost/latency/quality tradeoff
- [ ] Re-ingestion trigger is defined (on update, on comment, on close)
- [ ] Grounding guardrail is explicit — no fallback to general LLM knowledge
- [ ] Citation requirement is stated — sources must be returned in the response

### Gaps to Flag
List anything that is:
- Missing from the spec but required by the assignment
- Contradictory between two spec documents
- Underspecified (implementation would need to guess)

## Output Format
1. **Completeness Score** — X / 10 with brief justification
2. **Issues Found** — table with: Document | Section | Issue | Severity (BLOCKING / WARNING / MINOR)
3. **Suggested Additions** — draft text the author can paste into the spec to fill gaps
