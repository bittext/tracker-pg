# Journeys and skills

A Sierra agent is not a single mega-prompt. It is **goals + guardrails + composable skills** assembled into journeys.

## Briefing

**Journeys** (Studio language) are step-by-step workflows: the customer path the agent is allowed to run. You can write them from scratch or generate them from existing SOPs / operating procedures.

**Skills** (SDK language) are mix-and-match capabilities. Sierra’s own examples: **triage**, **respond**, **confirm**. Skills get composed into a workflow for one use case (return, warranty, churn save, mortgage origination).

Shared control knobs:

- **Goals** — what “done” means (the same idea as outcome pricing).
- **Guardrails** — policy, brand tone, what the agent must never do.
- **Tuning** — per-workflow flexibility: how creative vs deterministic this path is allowed to be. Insights can experiment on “levels of determinism” and hand-off rules.
- **Tools and dynamic data** — live reads/writes to systems and knowledge, not a frozen FAQ blob.

Studio **Agent Traces** and SDK **logic traces / API inspection** exist because a journey is a decision graph. If you cannot see which skill, tool, and knowledge article fired, you cannot debug it.

Ghostwriter and Studio both claim you can generate journeys from SOPs, transcripts, whiteboard photos, and SME audio — then you *review* before go-live.

## How it fits Sierra

- No-code authoring: [Agent Studio](../studio/agent-studio.md)
- Code authoring: [Agent SDK](agent-sdk.md)
- Generation: [Ghostwriter](ghostwriter.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- What is the canonical artifact — a Studio journey, an SDK module, or both synced?
- How do you version a skill that is reused across ten journeys?
