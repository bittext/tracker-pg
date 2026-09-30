# Knowledge

Grounding layer: Help Center, FAQs, policies — plus a loop that writes new articles from live care work.

## Briefing

Sierra’s knowledge engine is how the agent stays accurate without stuffing every policy into a prompt.

**In Studio**

- View, manage, and edit knowledge: Help Center content, FAQs, policies.
- **Knowledge gaps** — Sierra clusters themes the agent cannot resolve because the corpus is missing them.
- **Expert Answers** — draft articles from how care reps actually resolved edge cases. Edit, approve, publish; the article becomes part of the agent’s grounding. Same drafts can feed [Live Assist](../integrations/contact-center-and-handoff.md) so humans and the agent share one brain.
- Dynamic data from integrations sits beside documents so “what’s my order status?” is a tool call, not a stale paragraph.

**In Insights 2.0**

- **Explorer** finds friction themes in conversation volume.
- Expert Answers then looks at how humans solved those cases and produces a review-ready article. Publish from Insights; the agent updates.

This is the “use AI to improve your AI” loop: gap → human resolution → article → better containment, without a separate knowledge-management project every week.

If knowledge is treated as a one-time crawl of the public FAQ, the agent will fail the exact cases Sierra’s examples care about (loyalty rules, billing changes, policy exceptions).

## How it fits Sierra

- Authoring: [Agent Studio](agent-studio.md)
- Discovery: [Insights](../operations/insights-and-monitoring.md)
- Actions still need [systems](../integrations/systems-and-actions.md) — knowledge is not a substitute for a write API

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- How are conflicting articles resolved (policy v3 vs a year-old Help Center page)?
- What citation does the customer (or auditor) see when the agent answers from Expert Answers?
