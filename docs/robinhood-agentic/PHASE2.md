# Phase 2: Robinhood Agentic guarded execution

Status: **implemented** — order review/approval, token refresh, scheduled sync.

## What Phase 2 adds

| Component | Purpose |
|-----------|---------|
| Sidecar `POST /v1/refresh-token` | OAuth refresh (same as `phase0_oauth.py --refresh`) |
| Sidecar `POST /v1/review-order` | MCP `review_equity_order` dry-run |
| Sidecar `POST /v1/place-order` | MCP `place_equity_order` (Agentic account only) |
| Flyway `V47__robinhood_agentic_phase2.sql` | Per-user guardrails + order audit log |
| Spring order API | Review → approve/reject → place |
| Finance → Robinhood panel | Guardrails, propose order, pending approvals |
| `RobinhoodAgenticSyncScheduler` | Cron sync when `SYNC_CRON` is set |
| Token refresh | Auto-refresh on HTTP 401 before retry |

## Enable execution on Lightsail

Add to `.env.stack` (in addition to Phase 1 vars):

```bash
TRACKER_FINANCE_ROBINHOOD_AGENTIC_EXECUTION_ENABLED=true
# Optional:
TRACKER_FINANCE_ROBINHOOD_AGENTIC_SYNC_CRON=0 */30 * * * *
TRACKER_FINANCE_ROBINHOOD_AGENTIC_DEFAULT_MAX_ORDER_NOTIONAL=500
```

Rebuild stack:

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

## User flow

1. Connect tokens (Phase 1) and sync positions.
2. Configure **Order guardrails** — require approval (default on), max notional, symbol whitelist.
3. **Review order** — calls MCP `review_equity_order`; order saved as `pending_approval`.
4. **Approve** — calls MCP `place_equity_order` on the Agentic account only.
5. **Reject** — marks order rejected (no MCP write).

When **Require approval** is off and server execution is enabled, review auto-places if guardrails pass.

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/finance/robinhood/agentic/settings` | Guardrails + server execution flag |
| PUT | `/api/finance/robinhood/agentic/settings` | Update per-user guardrails |
| GET | `/api/finance/robinhood/agentic/orders` | Order history |
| POST | `/api/finance/robinhood/agentic/orders/review` | Review (and optionally place) |
| POST | `/api/finance/robinhood/agentic/orders/{id}/approve` | Place pending order |
| POST | `/api/finance/robinhood/agentic/orders/{id}/reject` | Reject pending order |

### Review request body

```json
{
  "symbol": "AAPL",
  "side": "buy",
  "type": "market",
  "quantity": 1
}
```

Limit orders require `limitPrice`. Use `amount` instead of `quantity` for dollar-based market orders if MCP supports it.

## Safety defaults

- `TRACKER_FINANCE_ROBINHOOD_AGENTIC_EXECUTION_ENABLED=false` until you opt in
- Per-user **Require approval before placing** defaults to `true`
- Orders only target the Agentic account (`agentic_allowed: true`)
- `max_order_notional` enforced from user settings or server default
- Optional `allowed_symbols` comma whitelist
- Full audit in `robinhood_agentic_orders`

## Token refresh

Store `refresh_token` when saving tokens (paste full `.tokens.json`). On MCP 401, the API refreshes via the sidecar and retries once.

## Scheduled sync

Set `TRACKER_FINANCE_ROBINHOOD_AGENTIC_SYNC_CRON` (Spring cron, UTC). When empty, the scheduler no-ops. Example: `0 */30 * * * *` every 30 minutes.

See [PHASE1.md](./PHASE1.md) for Phase 1 setup and [PHASE0.md](./PHASE0.md) for MCP tool catalog.
