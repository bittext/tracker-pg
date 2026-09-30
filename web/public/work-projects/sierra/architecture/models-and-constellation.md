# Models and constellation

Sierra does not bet the agent on one LLM. The agent is a **constellation** with supervisors and deterministic gates.

## Briefing

From Trust and reliability:

- **Constellation of models** — frontier, open-weight, and proprietary/specialist models. Different models for decisions, understanding the user, and generating the reply.
- **Automatic provider failover** — switch LLMs to keep performance and survive an outage. Continuity is a product feature, not an ops runbook you invent.
- **Supervisor models** — extra layers around the non-deterministic core to cut hallucinations, enforce security, and block abuse. Later AIUC-1 coverage (Sep 2026) describes the same idea: supervisors can correct, block, or escalate in real time; **deterministic guards** cover things that must never be left to judgment (authentication, access).
- **Secure integration** — when the agent hits a system of record, that path is supposed to be deterministic and policy-controlled, even if the language model is not.
- **Data rule** — Agent SDK page: customer data is **not used to train models**; Trust page: data is only used as instructed and never shared with other customers.

Implication for builders: you tune **goals, guardrails, knowledge, and tools**, not “the prompt for GPT-x.” Model identity can change under you. Tests and traces matter more than a frozen model string.

## How it fits Sierra

- Safety ops: [Trust and compliance](../operations/trust-and-compliance.md)
- Seeing why a reply happened: [Agent Studio traces](../studio/agent-studio.md), SDK debugging
- Outcome still defined in [journeys](../agents/journeys-and-skills.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- Can a tenant pin a model for a regulated journey, or is constellation always opaque?
- What is logged when a supervisor overrides the primary model?
