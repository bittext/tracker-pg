# Systems and actions

The line between a chatbot and a Sierra agent: **the agent can write back** to systems of record.

## Briefing

Sierra repeats the same trio: connect knowledge, connect systems of record, take action. Examples they use in public: process a return, update an order, retrieve account data, update a subscription, submit a warranty, run a payment (see PCI notes in [trust](../operations/trust-and-compliance.md)).

**Out-of-the-box:** **40+** pre-built integrations for third-party knowledge bases, systems of record, and contact centers. Studio 2.0: pick from the library, add credentials and endpoints, publish; tools show up for both Studio and SDK.

**Custom:** “opinionated integration framework,” configurable in Agent Studio, for proprietary systems. The point is not “write a random webhook and hope.” The framework is how tools get typed, authorized, and traced.

**Agent actions:** journeys call those tools in the moment — not “I’ll email you a link to do it yourself.” When the action is “give this to a human,” that is still an action: route with full context ([handoff](contact-center-and-handoff.md)).

Architecture constraint (Trust page): system-of-record access is **deterministic and controlled**. Language can be fuzzy; the refund API should not be. Pair this with supervisor models and auth guards.

If an integration is read-only, you will get a knowledgeable agent that still cannot close the outcome Sierra prices on.

## How it fits Sierra

- Where you wire them: [Agent Studio](../studio/agent-studio.md), [Agent SDK](../agents/agent-sdk.md)
- Memory join: [ADP](../architecture/agent-data-platform.md)
- Escalation: [Contact center and handoff](contact-center-and-handoff.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- What is the actual connector catalog (Salesforce, Shopify, Zendesk, …) as of our evaluation date?
- Idempotency and saga behavior when a journey dies mid-tool-call?
