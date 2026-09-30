# Insights and monitoring

The operations console: measure outcomes, research conversations, push knowledge back into the agent.

## Briefing

**Insights** is the hub for “is the agent working, and why.” Insights 2.0 (November 2025) added **Explorer** and tightened the loop with **Expert Answers**.

**Reporting**

- Key metrics: CSAT, case resolution, and whatever else the business names.
- One-click from a chart point into Explorer.
- Automated conversation tagging / categorization (what customers actually talk about — returns, a promo, a billing change).

**Explorer**

- Natural-language research over huge conversation sets (“Deep Research for customer conversations”).
- Aimed at CX, not a data team writing SQL.

**Experimentation**

- Value discovery (e.g. turn a cancel into a save / better plan).
- Conversation-design variants: hand-off rules, determinism.
- Intelligent decisioning using memory and profile ([ADP](../architecture/agent-data-platform.md)).

**Observability**

- Always-on review: conversations that need extra attention.
- Audit: knowledge sources, systems accessed, why an action happened.
- Alerting: abuse attempts, performance drops; hook into existing tools.

**Expert Answers** lives at the Insights ↔ Studio seam: Explorer finds friction → draft from how reps solved it → publish → agent (and Live Assist) get smarter.

Ghostwriter can watch the same metrics/releases and show up in Slack/Teams with a proposed change. Insights is the source of truth; Ghostwriter is one consumer.

## How it fits Sierra

- Knowledge publish path: [Knowledge](../studio/knowledge.md)
- Outcome definitions: [Pricing](../product/pricing-and-outcomes.md)
- Traces while building: [Agent Studio](../studio/agent-studio.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- What is the retention window for raw conversations vs aggregates?
- Can Explorer answer be exported as a durable report for legal / QA sampling?
