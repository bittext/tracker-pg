# Phase 1: Robinhood Agentic read-only sync

Status: **implemented** — sidecar + API + Finance UI for live position sync.

## What Phase 1 adds

| Component | Purpose |
|-----------|---------|
| `robinhood-agent-svc` (FastAPI) | MCP client; `POST /v1/sync` |
| Flyway `V45__robinhood_agentic.sql` | Tokens, positions, sync log |
| Spring `RobinhoodAgenticController` | REST under `/api/finance/robinhood/agentic/*` |
| Finance → Robinhood panel | Paste tokens, sync, view positions |

CSV import and historical P&amp;L are unchanged.

## Enable on Lightsail

1. Add to `.env.stack`:

```bash
TRACKER_FINANCE_ROBINHOOD_AGENTIC_ENABLED=true
TRACKER_FINANCE_ROBINHOOD_AGENTIC_TOKEN_ENCRYPTION_KEY=your-long-random-passphrase
```

2. Rebuild stack (includes `robinhood-agent` container):

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

3. In the web app: **Finance → Robinhood → Agentic live sync**
   - Paste contents of `robinhood-agent-svc/.tokens.json` from Phase 0 OAuth
   - Click **Save tokens** → **Sync now**

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/finance/robinhood/agentic/status` | Connection + last sync |
| POST | `/api/finance/robinhood/agentic/tokens` | `{ "accessToken", "refreshToken?" }` |
| POST | `/api/finance/robinhood/agentic/sync` | Pull portfolio/positions via MCP |
| GET | `/api/finance/robinhood/agentic/positions` | Cached positions |
| DELETE | `/api/finance/robinhood/agentic/connection` | Remove tokens + positions |

## Sidecar

```bash
curl -s http://127.0.0.1:8020/health   # from api container network: robinhood-agent:8020
```

Phase 1 sync pulls the **Agentic account** by default (`TRACKER_FINANCE_ROBINHOOD_AGENTIC_SYNC_DEFAULT=false`). Set to `true` to also sync your primary Robinhood account.

Internal sync flow: `get_accounts` → agentic (+ optional default) → `get_portfolio` + `get_equity_positions` + `get_option_positions` when available. Missing **Value** is filled via row price fields or batched `get_equity_quotes` (qty × last trade).

**Options:** Robinhood is rolling out option read/write tools. If `phase0_inventory.py` reports only **23 tools** and no `get_option_positions`, live option contracts cannot sync yet — only equities. Re-check after Robinhood expands your MCP catalog.

## Security

- OAuth tokens encrypted at rest when `TOKEN_ENCRYPTION_KEY` is set (Plaid-style AES-GCM)
- Phase 1 does **not** call `place_equity_order`
- Per-user scoping via `owner_user_id`

## Next (Phase 2)

See [PHASE2.md](./PHASE2.md) — **implemented**: strategy guardrails, `review_equity_order` → `place_equity_order` bridge, token refresh, scheduled sync cron.

See [PHASE0.md](./PHASE0.md) for MCP tool catalog and account schema.
