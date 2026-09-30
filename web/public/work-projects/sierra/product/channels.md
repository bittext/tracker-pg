# Channels

One agent, many doors. Sierra’s 2025 thesis: stop running a different experience per channel.

## Briefing

**Build once, deploy everywhere.** Official channel list (product + Agent OS 2.0, November 2025):

- Chat (web / in-app)
- Voice / phone
- Email
- SMS
- WhatsApp and other messaging
- **ChatGPT** (publish the agent; control which journeys, data, and capabilities appear)
- **Contact center** via Live Assist (the same agent sitting next to a human associate)

Sierra claims **59 languages** and **24/7/365**. Agent OS 2.0 also mentions Gemini as a future/adjacent distribution surface alongside ChatGPT.

Interactive **universal web attachments** (maps, fillable forms, charts) are meant to work across those channels so a journey is not rewritten per surface.

The strategic claim: the conversation *is* the interface — product discovery, sales, sign-up, troubleshooting, loyalty, retention — not a help-widget afterthought. That only works if memory and systems of record are shared; otherwise each channel is a goldfish. That is why [Agent Data Platform](../architecture/agent-data-platform.md) exists.

Voice is not “chat with TTS.” Studio has **Voice Sims** specifically for transcription, background noise, and speaker variation. Treat voice as a first-class test surface, not a checkbox.

## How it fits Sierra

- Runtime: [Agent OS](../architecture/agent-os.md)
- Brand per surface: [Branding and controls](../studio/branding-and-controls.md)
- Humans in the loop: [Contact center and handoff](../integrations/contact-center-and-handoff.md)

## Your notes

_Add dated bullets. Do not overwrite the briefing._

## Open questions

- Which channel is the first production cut, and which journeys are *excluded* from ChatGPT publish?
- How does email/SMS state work when the customer switches to voice mid-case?
