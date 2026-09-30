# Trust and compliance

Enterprise gate: data isolation, supervisor layers, payment isolation, named certifications.

## Briefing

Sierra’s Trust page (and Trust Center) is the public control list. Treat “we are compliant” as **their** claim; confirm the current report in the Trust Center before a real deployment.

**Compliance names they list:** SOC 2, HIPAA, GDPR, PCI, FedRAMP High, CCPA, CSA STAR, ISO 27001, ISO 42001. Unite.AI (17 Sep 2026) additionally reports **AIUC-1** after a Schellman audit, plus SOC 2 Type II, ISO/IEC 27001:2022, ISO/IEC 42001:2023, PCI DSS 4.0.1, EU AI Act listed on the Trust Center.

**Runtime safety**

- Supervisor models around LLMs (hallucination, security, abuse).
- Deterministic, policy-controlled access to systems of record.
- Topic/keyword filters for off-limits subjects.
- PII encrypted and masked.
- Data used only as instructed; **not shared with other customers**; Agent SDK page: **not used to train models**.

**Payments**

- Cardholder data on **dedicated PCI-certified infrastructure**.
- Does **not** touch Sierra’s core platform, LLMs, or persistent storage.
- **PCI DSS Level 1 Service Provider** (their wording).
- Voice and chat card/ACH at “thousands of transactions daily” (their claim).

**Operations implication:** auth, refunds, and “can this user see this account?” belong in deterministic guards, not in a creative skill. Regulated journeys (mortgage, patient auth) are why constellation + supervisors exist.

Do not copy audit reports into this library. Link them from **Your notes** with a date.

## How it fits Sierra

- Model stack: [Models and constellation](../architecture/models-and-constellation.md)
- Tooling: [Systems and actions](../integrations/systems-and-actions.md)
- Brand filters: [Branding and controls](../studio/branding-and-controls.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- Do we need a BAA (HIPAA) or DPA in place before any pilot data leaves our VPC?
- FedRAMP High: is that Sierra’s own authorization or a path for public-sector tenants?
