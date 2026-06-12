# Robinhood Agentic Trading — Phase 0 spike

Discovery toolkit for Robinhood's MCP server (`https://agent.robinhood.com/mcp/trading`).
Phase 1 will promote this into a production sidecar; for now it is local-only scripts.

## Prerequisites

- Python 3.11+
- Robinhood account in good standing
- **Desktop browser** for Agentic account onboarding ([Robinhood docs](https://robinhood.com/us/en/support/articles/agentic-trading-overview/))
- Small funded budget in the **Agentic account** (isolated from primary account)

## Quick start

```bash
cd robinhood-agent-svc
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 1. Unauthenticated probes (safe anytime)
python phase0_discover.py --write findings/discovery.json

# 2. OAuth — opens browser, saves .tokens.json
python phase0_oauth.py

# If you are on AWS/Lightsail SSH (console browser cannot reach localhost):
python phase0_oauth.py --manual

# 3. List MCP tools after auth
python phase0_inventory.py

# Optional: probe read-like tools with empty args
python phase0_inventory.py --probe-read-tools

# Recommended: chained reads using account_number from get_accounts
python phase0_inventory.py --probe-chain
```

## Cursor MCP (parallel path)

You can also connect directly in Cursor without these scripts:

1. Settings → Cursor Settings → Tools & MCPs → Connect
2. Paste: `https://agent.robinhood.com/mcp/trading`
3. Authenticate when prompted (desktop onboarding for Agentic account)

Use Cursor to explore natural-language portfolio queries; use `phase0_inventory.py` to capture
structured tool names/schemas for Phase 1 implementation.

## Files

| File | Purpose |
|------|---------|
| `phase0_discover.py` | OAuth metadata, client registration, 401 probe |
| `phase0_oauth.py` | PKCE OAuth flow → `.tokens.json` |
| `phase0_inventory.py` | MCP initialize + `tools/list` → `findings/tool-inventory.json` |
| `mcp_client.py` | Minimal Streamable HTTP MCP client |
| `findings/discovery.json` | Committed baseline (no secrets) |
| `findings/tool-inventory.json` | Gitignored; fill after auth |

## Security

- `.tokens.json` and `.oauth-client.json` are gitignored
- Start with read-only inventory before calling trade tools
- Trades execute only in the **Agentic account**, not your primary account

See also: [docs/robinhood-agentic/PHASE0.md](../docs/robinhood-agentic/PHASE0.md) · [PHASE1.md](../docs/robinhood-agentic/PHASE1.md) (read-only sync)

## OAuth from AWS / Lightsail SSH

The Lightsail or EC2 **console browser cannot complete OAuth** — Robinhood redirects to
`http://127.0.0.1:8765/callback`, which must hit the machine where the script runs, or
your laptop when using manual mode.

**Recommended:** run OAuth on your Mac, not on the server:

```bash
# On your Mac (clone repo or copy robinhood-agent-svc/)
cd robinhood-agent-svc
python phase0_oauth.py --manual
```

Open the printed URL in **Chrome/Safari on your Mac**. After login, copy the full
`http://127.0.0.1:8765/callback?code=…&state=…` URL from the address bar (the page
may not load) and paste it into the terminal.

Then copy `.tokens.json` to the server if needed, or run `phase0_inventory.py` locally.

**Alternative:** Cursor MCP on your Mac (Settings → Tools & MCPs) skips these scripts entirely.
