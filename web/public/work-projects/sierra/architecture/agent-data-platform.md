# Agent Data Platform (ADP)

ADP is the memory and intelligence layer of Agent OS: unify what was *said* with what was *done*, then decide.

## Briefing

Announced at Sierra Summit 2025. Sierra’s problem statement: even good agents are transactional — one chat, one ticket, done — because they lack memory and a join to systems of record.

ADP does two jobs:

1. **Unified customer context.** Merge unstructured conversation (calls, chats, emails) with structured systems (CRM, billing, inventory, policies, transactions). It can sit on existing warehouses / systems of record rather than demanding a rip-and-replace CDP. Every conversation is supposed to enrich memory for the next one.
2. **Intelligent decisioning.** Not a static next-best-action table. Personalize high-value actions per customer (plan change, renewal flag, preemptive fix) while still honoring guardrails. Sierra’s words: move from answering questions to anticipating needs; from conversations to relationships.

Because ADP is “powered by Agent OS,” the same memory is available on chat, phone, SMS, email, contact-center conversations, or Headless API. That is the technical reason [channels](../product/channels.md) can be one agent.

Examples they give: greet by name, remember prior preferences, suggest a better plan, flag a renewal, resolve an issue before the customer asks.

If ADP is weak or unconnected, Studio journeys will look smart in simulation and amnesiac in production.

## How it fits Sierra

- Runtime: [Agent OS](agent-os.md)
- Actions still go through [systems and actions](../integrations/systems-and-actions.md)
- Personalization experiments: [Insights](../operations/insights-and-monitoring.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- Retention, consent, and right-to-erasure: how does ADP honor GDPR/CCPA deletes across channels?
- What is stored as embedding vs source-of-truth pointer back to CRM?
