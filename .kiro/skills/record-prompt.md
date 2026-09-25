# Skill: Record Prompt History

Every prompt sent to the AI assistant in this project must be recorded.

## Trigger
After every substantive prompt (any prompt that produces code, spec text, analysis, or a decision), append an entry to `docs/prompt-history.md`.

## Entry Format
```markdown
## [YYYY-MM-DD HH:MM] — [Short label describing the task]

**Prompt:**
> Paste the exact prompt text here

**Summary of AI response:** One sentence describing what the AI produced.

**Accepted / Modified / Rejected:** State which, and briefly note any changes made if Modified.

---
```

## Rules
- Record EVERY prompt — do not skip prompts that produced wrong output (those are especially valuable)
- If the AI output was wrong, set status to **Rejected** or **Modified** and link to the corresponding entry in `docs/ai-mistakes.md`
- Label should be specific: "Generate TicketService unit tests" not just "Tests"
- Timestamps in local time (ISO-8601 format)

## Where to Save
- Primary log: `docs/prompt-history.md`
- Raw session exports (if available): `.specstory/history/YYYY-MM-DD_label.md`
