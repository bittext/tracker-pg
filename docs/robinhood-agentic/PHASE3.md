# Phase 3: AI auto-trade on Agentic account (max controls)

Status: **implemented** — Predicts-driven signals, layered guardrails, scheduled + manual evaluate.

## What Phase 3 adds

| Component | Purpose |
|-----------|---------|
| Flyway `V48__robinhood_agentic_auto_trade.sql` | Per-user auto-trade settings, order `source`, run audit log |
| `RobinhoodAgenticAutoTradeService` | Evaluates Predicts tickers → buy/sell signals → Phase 2 review/place |
| `RobinhoodAgenticAutoTradeScheduler` | Polls all connected users when server auto-trade is enabled |
| Finance → Robinhood panel | AI auto-trade controls, kill switch, **Run evaluate now** |
| REST `/agentic/auto-trade/*` | Manual evaluate + run history |

## Architecture

```
Predicts tickers (per user)
        ↓
FinBERT / StockTwits summary (positivity, spikeZ, mentions)
        ↓
Auto-trade engine (guardrails)
        ↓
Phase 2 review_equity_order → approve or auto-place
        ↓
Agentic account only (MCP place_equity_order)
```

## Enable on Lightsail

Requires Phase 1 (sync) and Phase 2 (execution). Add to `.env.stack`:

```bash
TRACKER_FINANCE_ROBINHOOD_AGENTIC_ENABLED=true
TRACKER_FINANCE_ROBINHOOD_AGENTIC_EXECUTION_ENABLED=true
TRACKER_FINANCE_ROBINHOOD_AGENTIC_AUTO_TRADE_ENABLED=true
# Optional poll interval (ms, default 300000 = 5 min):
# TRACKER_FINANCE_ROBINHOOD_AGENTIC_AUTO_TRADE_POLL_MS=300000
```

Rebuild:

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

Then in **Finance → Robinhood**:

1. Connect Agentic OAuth tokens and sync.
2. Configure **Order guardrails** (max notional, symbol whitelist, manual approval).
3. Add tickers under **Finance → Predicts** (auto-trade only evaluates tracked symbols).
4. Configure **AI auto-trade** thresholds and limits.
5. Enable **AI auto-trade** per user (still requires server flag above).
6. Use **Run evaluate now** to test, or wait for the scheduler.

## Signal logic

For each Predicts tracked ticker:

| Signal | Conditions |
|--------|------------|
| **Buy** | No Agentic equity position · positivity ≥ `min_positivity_buy` · spikeZ ≥ `min_spike_z` · mentions (24h) ≥ `min_mentions_24h` |
| **Sell** | Has Agentic equity position · positivity ≤ `max_positivity_sell` |

Each candidate order uses configured `order_quantity` and passes through Phase 2 MCP review with `source=auto`.

## Guardrails (defense in depth)

| Layer | Control |
|-------|---------|
| Server | `AUTO_TRADE_ENABLED=false` by default |
| Server | `EXECUTION_ENABLED` must be true for any placement |
| User | **Enable AI auto-trade** (off by default) |
| User | **Kill switch** — immediate pause of all auto orders |
| User | **Require approval for auto orders** (default on) — separate from manual `require_approval` |
| User | Symbol whitelist, max order notional (Phase 2) |
| User | Max trades per day, max daily notional, per-symbol cooldown |
| User | US market hours only (9:30–16:00 ET, weekdays) |
| Audit | `robinhood_agentic_auto_trade_runs` + order `source` + `auto_signal_json` |

**Emergency stop** in the UI sets kill switch + disables auto-trade and saves immediately.

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| PUT | `/api/finance/robinhood/agentic/settings` | Includes all auto-trade fields (see DTO) |
| POST | `/api/finance/robinhood/agentic/auto-trade/evaluate` | Run evaluation for current user now |
| GET | `/api/finance/robinhood/agentic/auto-trade/runs` | Recent auto-trade run audit rows |

Settings response includes `autoTradeServerEnabled` (read-only mirror of server env).

## Default thresholds (new users)

| Setting | Default |
|---------|---------|
| `auto_trade_require_approval` | `true` |
| `auto_trade_min_positivity_buy` | `15` |
| `auto_trade_max_positivity_sell` | `-15` |
| `auto_trade_min_spike_z` | `1.5` |
| `auto_trade_min_mentions_24h` | `5` |
| `auto_trade_order_quantity` | `1` |
| `auto_trade_max_trades_per_day` | `3` |
| `auto_trade_cooldown_minutes` | `60` |
| `auto_trade_market_hours_only` | `true` |

## Safety notes

- Auto-trade never targets non-Agentic accounts.
- When **Require approval for auto orders** is on, orders stay `pending_approval` like manual Phase 2 flow.
- When off and all guardrails pass, review auto-places (same as manual with approval off).
- Rotate OAuth tokens if exposed; store full `.tokens.json` including `refresh_token`.

See [PHASE2.md](./PHASE2.md) for order review/approve flow and [PHASE1.md](./PHASE1.md) for sync setup.

## Admin console (Phase 3+)

**Admin → Finance → Trading → Robinhood Agentic** provides:

| Feature | Description |
|---------|-------------|
| **Default guardrails** | Editable DB defaults applied to new users via **Apply to user** |
| **Tracker** | Pending approval queue, recent orders, auto-trade runs, alert log |
| **Evaluate** | Run auto-trade for all users or a specific user ID |
| **Approve/Reject** | Cross-user order actions (ADMIN role) |
| **Approval alerts** | Email/SMS when orders enter `pending_approval` (uses Admin → Finance → Notifications destinations; toggles on defaults form) |

API base: `/api/admin/finance/agentic/*`
