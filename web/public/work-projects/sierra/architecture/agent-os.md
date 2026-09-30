# Agent OS

Agent OS is the runtime: one production-grade agent, authored once, executed on every surface.

## Briefing

Sierra’s stack talk is **Agent OS**, not “a chatbot product plus some APIs.” Agent OS is what lets you:

1. **Author** the agent in either [Agent Studio](../studio/agent-studio.md) or the [Agent SDK](../agents/agent-sdk.md) using the same building blocks (goals, guardrails, journeys, tools).
2. **Deploy** that agent to chat, voice, email, SMS, messaging, ChatGPT, and the contact center without rewriting the brain.
3. **Govern** behavior: supervisor models around non-deterministic LLMs, deterministic tool/auth paths, simulations, traces.
4. **Improve** via [Insights](../operations/insights-and-monitoring.md) and [Ghostwriter](../agents/ghostwriter.md).

**Agent OS 2.0** (Sierra Summit, 5 November 2025, Bret Taylor & Clay Bavor) packaged eight products around three shifts:

| Shift | What changed |
| --- | --- |
| Multi-channel → single agent | ChatGPT publish + Live Assist in the contact center |
| Technology → product | Agent Studio 2.0 (Journeys, Workspaces, integrations in the UI) + Insights 2.0 |
| Conversations → relationships | [Agent Data Platform](agent-data-platform.md) as the memory/intelligence layer |

A **Headless API** is mentioned on the ADP post as another consume-the-agent path (not only a widget). Use that when the agent should sit behind your own UI.

Mental model: Studio/SDK are IDEs. Agent OS is the OS. ADP is memory. Insights is observability. Ghostwriter is the meta-agent.

## How it fits Sierra

- Memory: [Agent Data Platform](agent-data-platform.md)
- Models: [Models and constellation](models-and-constellation.md)
- Surfaces: [Channels](../product/channels.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- What is actually in the Headless API (session, tools, streaming, auth)?
- Where does tenant isolation live — conversation store, ADP, tool gateway?
