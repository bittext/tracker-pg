# webull-quote-svc

Sidecar for live US stock and option marks via [Webull OpenAPI](https://developer.webull.com/apis/docs/).

Used by tracker-pg when Robinhood Agentic quotes are unavailable. Requires:

- `WEBULL_APP_KEY` — App Key from Developer Tools → Generate Key (used for request signing)
- `WEBULL_APP_SECRET` — App Secret from the same screen
- `WEBULL_USER_ID` — optional account User ID (separate from App Key; sent as `wb-user-id` header)
- `WEBULL_ACCESS_TOKEN` — optional pre-approved access token if your account requires 2FA
- An **OpenAPI Advanced Quotes** subscription (separate from the mobile app)

## Endpoints

- `GET /health`
- `POST /v1/quotes` — `{ "symbols": ["NBIS"], "options": [{ "instrument_id", "symbol", "strike", "expiration", "option_type" }] }`
