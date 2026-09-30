# Contact center and handoff

AI is not supposed to trap the customer. Escalation is a designed skill; Live Assist puts the same agent next to the human.

## Briefing

**Classic handoff (SDK / Studio):** when the issue needs a person, the agent routes to the right team and **auto-generates a detailed summary** so the customer does not repeat themselves. Handoff rules are something Insights can A/B (conversation-design experiments).

**Live Assist (Agent OS 2.0, 5 November 2025):** the agent as a real-time copilot *inside* the contact center / in-person conversation.

- Embedded on the associate’s screen; no tab-sprawl.
- Captures details as the customer speaks.
- Searches the **same** help centers and knowledge that the AI agent uses.
- Surfaces answers, next action, past interactions, customer context.
- Can trigger actions (return, refund) in one click.
- Same goals and guardrails as chat/voice/email/ChatGPT — “build once.”
- Conversations feed back into Insights, which should improve both handle time and the autonomous agent.

Sierra’s line: not every conversation should be AI-only; Live Assist is how humans get “read millions of words a minute, use multiple tools at once, stay on brand.”

Clear’s public quote (Adam Luebbers): if a member wants a human, that is always an option — “augmentation, not elimination.”

Design rule: a journey without a handoff path is a containment trap. A handoff without a summary and tool context is just a cold transfer.

## How it fits Sierra

- Shared brain: [Knowledge](../studio/knowledge.md), [ADP](../architecture/agent-data-platform.md)
- Actions: [Systems and actions](systems-and-actions.md)
- QA of the human+AI loop: [Insights](../operations/insights-and-monitoring.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- Which CCaaS platforms are first-class (Genesys, NICE, Five9, Amazon Connect, …)?
- Does Live Assist work for outbound / sales floors or only inbound care?
