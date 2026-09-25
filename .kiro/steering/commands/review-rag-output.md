---
inclusion: manual
---

# Command: Review RAG Output (Hallucination / Grounding Check)

Use this command to evaluate an AI assistant answer for grounding quality — whether it is based strictly on retrieved ticket data or contains fabricated content.

## How to invoke
Paste the question and the AI response, then ask:
> "Review this RAG output using the review-rag-output command"

## Grounding Verification Checklist

### Source Citation
- [ ] Does the response include at least one `ticketId` citation (e.g. TKT-1001)?
- [ ] Are the cited ticket IDs real tickets that exist in the database?
- [ ] Does the `sources` array in the JSON response match the IDs mentioned in the answer text?

### Faithfulness (Answer vs Retrieved Context)
- [ ] Is every factual claim in the answer traceable to a specific retrieved chunk?
- [ ] Are there any claims that could ONLY come from general LLM knowledge (not from tickets)?
- [ ] Does the answer introduce any entities (names, dates, amounts, error codes) not present in the retrieved tickets?

### No-Match Behaviour
- [ ] If no tickets were retrieved above the similarity threshold, does the response say "No relevant tickets were found to answer this question" — exactly?
- [ ] Does the response avoid generating a plausible-sounding but fabricated answer when sources are empty?

### Scope Guardrail
- [ ] Does the answer stay within the scope of the retrieved tickets?
- [ ] Does it avoid adding advice, recommendations, or explanations not grounded in ticket data?

### Common Hallucination Patterns to Detect
1. **Date fabrication** — AI invents a resolution date not in any ticket
2. **Name invention** — AI generates an assignee name not in the retrieved context
3. **Cause fabrication** — AI explains a root cause using general knowledge, not ticket descriptions
4. **Ticket ID invention** — AI cites TKT-9999 which doesn't exist
5. **Over-generalisation** — AI says "all payment failures are caused by X" when only one ticket mentions it
6. **Confident no-context answer** — AI answers a question confidently when no tickets were retrieved

## How to Test for Hallucination
Ask these calibration questions to probe the guardrail:
- "What is the capital of France?" → must return no-match response (out of scope)
- "What is the best practice for microservices?" → must return no-match response
- Ask about a specific ticket ID that does NOT exist → must not fabricate details
- Ask a valid question but with all tickets deleted from the store → must return no-match

## Output Format
1. **Grounding Score** — GROUNDED | PARTIALLY GROUNDED | HALLUCINATED
2. **Specific Violations** — list each ungrounded claim with evidence of what was or was not in the retrieved context
3. **Verdict** — Accept | Reject with one-sentence reason
4. **Log entry** — if HALLUCINATED, provide the `docs/ai-mistakes.md` entry to paste
