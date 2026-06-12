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

# 3. List MCP tools after auth
python phase0_inventory.py

# Optional: probe read-like tools with empty args
python phase0_inventory.py --probe-read-tools
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

See also: [docs/robinhood-agentic/PHASE0.md](../docs/robinhood-agentic/PHASE0.md)
