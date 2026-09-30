# Testing and release

Harden the agent like software: simulate, regress, review in a Workspace, then publish.

## Briefing

Sierra treats “the prompt worked in a demo” as insufficient. Official test surfaces:

**Simulations (Studio + SDK)**

- AI-powered evaluations against **defined outcomes** (same language as pricing).
- Large-scale, not a handful of golden chats.
- Ghostwriter auto-generates sims on every build/update and hunts edge cases.

**Regression**

- A saved suite that must pass before a new agent version ships.
- The point: a new journey for “holiday promo” must not break “cancel membership.”

**Voice Sims**

- Transcription, background noise, speaker variation — before the phone number goes live.
- If voice is a channel, this suite is not optional.

**Traces**

- Studio: every decision, tool call, response while authoring.
- SDK: API calls and logic traces.

**Release mechanics**

- Studio 2.0 **Workspaces**: GitHub-style collaboration — treat publish as a review, not a save.
- Ghostwriter: show the proposed agent **before** go-live; humans approve.
- Dynamic updates (promos, outages) can ship faster than a full journey version; know which knob you are turning.

**Live monitoring after release** is Insights (alerts, conversations needing attention), not a substitute for the pre-release suite.

Failure mode to remember: simulations that only use clean typed English will green-light an agent that collapses on accented voice or angry SMS fragments.

## How it fits Sierra

- Authoring: [Agent Studio](../studio/agent-studio.md), [Agent SDK](../agents/agent-sdk.md)
- Generator: [Ghostwriter](../agents/ghostwriter.md)
- After go-live: [Insights](insights-and-monitoring.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- Can we import our own transcript corpus as the regression suite?
- What is the promotion path — Workspace → staging number → prod — and who signs off?
