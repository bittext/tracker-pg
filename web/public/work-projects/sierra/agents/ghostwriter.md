# Ghostwriter

The agent-building agent: describe the outcome, review the diff, ship.

## Briefing

Ghostwriter is Sierra’s meta-agent. Homepage copy: upload SOPs, transcripts, whiteboard photos, and audio recordings — or explain the goal in plain English — and it builds a production-ready, multilingual, multichannel agent with guardrails.

Three loops (product page):

**Build**

- Prompts, not clicks: change workflows, integrations, guardrails, tone, style.
- Build from what you have: SOPs, raw transcripts, SME audio interviews.
- Transparency: show what it built **before** anything is live.

**Test**

- Auto-generate simulations on every build/update.
- Hunt edge cases beyond the happy path.
- On failure: diagnose and propose the next change (product page: “propose the next change for your team to review”; older Studio-adjacent copy said it can implement the fix — treat auto-apply as something you confirm in product, not assume).

**Improve**

- Watch conversations, metrics, releases, experiments against goals/guardrails.
- Bring evidence to Slack or Teams with a proposed next step, then follow the result after release.
- Surface unmet needs, not only answers to tickets you already knew about.

Sierra’s slogan for this: **Agents as a Service** — software where you describe the outcome and agents do the build/execute/improve work.

Ghostwriter does not replace [Insights](../operations/insights-and-monitoring.md) or human approval. It is the worker that turns those signals into a reviewable agent change.

## How it fits Sierra

- Authoring UI: [Agent Studio](../studio/agent-studio.md)
- Quality gate: [Testing and release](../operations/testing-and-release.md)
- Signal source: [Insights](../operations/insights-and-monitoring.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- What is the approval policy — Ghostwriter can merge to prod, or only open a Workspace change?
- How does it treat regulated journeys that must stay deterministic?
