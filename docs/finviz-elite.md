# Finviz Elite (Markets)

Tracker integrates Finviz Elite’s **CSV export API** (`export.ashx`) for Markets research. The API key stays on the server and is never sent to the browser.

## Setup

1. Subscribe to [Finviz Elite](https://elite.finviz.com/) and open your account page to copy the **API key**.
2. On the Lightsail host, edit `.env.stack`:

```bash
TRACKER_FINANCE_FINVIZ_ELITE_ENABLED=true
TRACKER_FINANCE_FINVIZ_ELITE_API_KEY=your_elite_api_key_here
# Optional:
# TRACKER_FINANCE_FINVIZ_ELITE_CACHE_TTL_SECONDS=120
# TRACKER_FINANCE_FINVIZ_ELITE_UNIVERSE_ENABLED=true
```

3. Redeploy the API container so env vars load.

## What Tracker uses

| Feature | Endpoint |
|---------|----------|
| Status | `GET /api/markets/finviz/status` |
| Presets / signals | `GET /api/markets/finviz/presets`, `.../signals/{name}`, `.../screener` |
| Groups / news / options / portfolio | `GET /api/markets/finviz/groups\|news\|options\|portfolio` |
| Add to Your Watch | `POST /api/markets/finviz/watch` |

Export shape:

```text
GET https://elite.finviz.com/export.ashx?auth={KEY}&v={view}&f={filters}&s={signal}&o={sort}
```

Paste an Elite screener URL into the UI; Tracker strips `auth` if present and re-attaches the server key.

## Compliance

- Use only `export.ashx` (no HTML scraping).
- Personal Tracker automation with your own Elite subscription.
- Cache results (default 120s) and refresh manually in the UI to avoid rate pressure.
