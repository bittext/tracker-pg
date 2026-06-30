# webull-quote-svc

Sidecar for live US stock and option marks via [Webull OpenAPI](https://developer.webull.com/apis/docs/).

Used by tracker-pg when Robinhood Agentic quotes are unavailable. Requires:

- `WEBULL_USER_ID` — your Webull OpenAPI user id (shown in Developer Tools / API Keys)
- `WEBULL_APP_SECRET` — App Secret from the same screen
- Optional `WEBULL_APP_KEY_ID` — only if your portal shows a separate App Key id for signing (defaults to user id)
- An **OpenAPI Advanced Quotes** subscription (separate from the mobile app)

## Endpoints

- `GET /health`
- `POST /v1/quotes` — `{ "symbols": ["NBIS"], "options": [{ "instrument_id", "symbol", "strike", "expiration", "option_type" }] }`
