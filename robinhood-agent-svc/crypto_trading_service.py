"""Robinhood Crypto Trading API sync and orders."""

from __future__ import annotations

import base64
import datetime
import json
import logging
import uuid
from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Optional
from urllib.parse import urlencode

import httpx
from nacl.signing import SigningKey

LOGGER = logging.getLogger(__name__)

BASE_URL = "https://trading.robinhood.com"

ED25519_SEED_BYTES = 32


def normalize_base64(value: str) -> str:
    """Strip whitespace and restore padding for Robinhood-style pasted keys."""
    cleaned = "".join(value.split())
    if not cleaned:
        return ""
    pad = (-len(cleaned)) % 4
    if pad:
        cleaned += "=" * pad
    return cleaned


def decode_ed25519_seed(private_key_base64: str) -> bytes:
    normalized = normalize_base64(private_key_base64)
    if not normalized:
        raise ValueError("privateKeyBase64 is required")
    try:
        seed = base64.b64decode(normalized, validate=True)
    except Exception as exc:  # noqa: BLE001
        raise ValueError(
            "Private key must be the base64 Ed25519 seed (~44 characters) saved when you "
            "created the key pair — not the Robinhood API key. Paste the full string with no spaces."
        ) from exc
    if len(seed) != ED25519_SEED_BYTES:
        raise ValueError(
            f"Private key decoded to {len(seed)} bytes; Ed25519 seed must be exactly "
            f"{ED25519_SEED_BYTES} bytes. Check you pasted the private key, not the API key."
        )
    return seed


class RobinhoodCryptoTradingClient:
    def __init__(self, api_key: str, private_key_base64: str) -> None:
        self.api_key = api_key.strip()
        seed = decode_ed25519_seed(private_key_base64)
        self._signing_key = SigningKey(seed)
        self._client = httpx.Client(base_url=BASE_URL, timeout=30.0)

    def close(self) -> None:
        self._client.close()

    @staticmethod
    def _timestamp() -> int:
        return int(datetime.datetime.now(tz=datetime.timezone.utc).timestamp())

    def _auth_headers(self, method: str, path: str, body: str = "") -> dict[str, str]:
        timestamp = self._timestamp()
        message = f"{self.api_key}{timestamp}{path}{method}{body}"
        signed = self._signing_key.sign(message.encode("utf-8"))
        return {
            "x-api-key": self.api_key,
            "x-signature": base64.b64encode(signed.signature).decode("utf-8"),
            "x-timestamp": str(timestamp),
        }

    def _get(self, path: str) -> Any:
        headers = self._auth_headers("GET", path)
        response = self._client.get(path, headers=headers)
        if response.status_code == 401:
            raise PermissionError("Robinhood Crypto API rejected credentials (401)")
        if response.status_code == 403:
            raise PermissionError("Robinhood Crypto API forbidden (403) — check key permissions")
        if response.status_code >= 400:
            raise RuntimeError(
                f"Robinhood Crypto API error HTTP {response.status_code}: {response.text[:500]}"
            )
        return response.json()

    def _post(self, path: str, body: str) -> Any:
        headers = self._auth_headers("POST", path, body)
        headers["Content-Type"] = "application/json"
        response = self._client.post(path, headers=headers, content=body.encode("utf-8"))
        if response.status_code == 401:
            raise PermissionError("Robinhood Crypto API rejected credentials (401)")
        if response.status_code == 403:
            raise PermissionError("Robinhood Crypto API forbidden (403) — check key permissions")
        if response.status_code >= 400:
            raise RuntimeError(
                f"Robinhood Crypto API error HTTP {response.status_code}: {response.text[:500]}"
            )
        if not response.text.strip():
            return {}
        return response.json()

    def place_market_order(
        self,
        account_number: str,
        symbol: str,
        side: str,
        *,
        asset_quantity: str | None = None,
        quote_amount: str | None = None,
        client_order_id: str | None = None,
    ) -> dict[str, Any]:
        pair = str(symbol).strip().upper()
        if not pair.endswith("-USD"):
            pair = f"{pair}-USD"
        order_side = str(side).strip().lower()
        if order_side not in {"buy", "sell"}:
            raise ValueError("side must be buy or sell")
        if asset_quantity and quote_amount:
            raise ValueError("Specify asset_quantity or quote_amount, not both")
        if not asset_quantity and not quote_amount:
            raise ValueError("asset_quantity or quote_amount is required")

        market_config: dict[str, str] = {}
        if asset_quantity:
            market_config["asset_quantity"] = str(asset_quantity)
        else:
            market_config["quote_amount"] = str(quote_amount)

        payload = {
            "client_order_id": client_order_id or str(uuid.uuid4()),
            "side": order_side,
            "type": "market",
            "symbol": pair,
            "market_order_config": market_config,
        }
        body = json.dumps(payload, separators=(",", ":"))
        query = urlencode({"account_number": account_number.strip()})
        path = f"/api/v2/crypto/trading/orders/?{query}"
        result = self._post(path, body)
        if isinstance(result, dict):
            result.setdefault("client_order_id", payload["client_order_id"])
            result.setdefault("symbol", pair)
        return result if isinstance(result, dict) else {"result": result}

    @staticmethod
    def _paginate_results(initial: Any) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        payload = initial
        while isinstance(payload, dict):
            batch = payload.get("results")
            if isinstance(batch, list):
                for row in batch:
                    if isinstance(row, dict):
                        rows.append(row)
            next_url = payload.get("next")
            if not next_url:
                break
            # next is absolute URL; extract path+query for signing
            if isinstance(next_url, str) and next_url.startswith(BASE_URL):
                path = next_url[len(BASE_URL) :]
            else:
                break
            payload = None  # caller must fetch next page via signed GET
            break
        return rows

    def list_accounts(self) -> list[dict[str, Any]]:
        all_rows: list[dict[str, Any]] = []
        path = "/api/v2/crypto/trading/accounts/"
        while path:
            payload = self._get(path)
            if not isinstance(payload, dict):
                break
            batch = payload.get("results")
            if isinstance(batch, list):
                for row in batch:
                    if isinstance(row, dict):
                        all_rows.append(row)
            next_url = payload.get("next")
            if not next_url or not isinstance(next_url, str):
                break
            if next_url.startswith(BASE_URL):
                path = next_url[len(BASE_URL) :]
            else:
                break
        return all_rows

    def list_holdings(self, account_number: str) -> list[dict[str, Any]]:
        all_rows: list[dict[str, Any]] = []
        base_path = "/api/v2/crypto/trading/holdings/"
        query = urlencode({"account_number": account_number})
        path = f"{base_path}?{query}"
        while path:
            payload = self._get(path)
            if not isinstance(payload, dict):
                break
            batch = payload.get("results")
            if isinstance(batch, list):
                for row in batch:
                    if isinstance(row, dict):
                        all_rows.append(row)
            next_url = payload.get("next")
            if not next_url or not isinstance(next_url, str):
                break
            if next_url.startswith(BASE_URL):
                path = next_url[len(BASE_URL) :]
            else:
                break
        return all_rows

    def best_bid_ask(self, symbols: list[str]) -> dict[str, Decimal]:
        if not symbols:
            return {}
        base_path = "/api/v2/crypto/marketdata/best_bid_ask/"
        params: list[tuple[str, str]] = []
        for sym in symbols:
            params.append(("symbol", sym))
        path = f"{base_path}?{urlencode(params)}"
        payload = self._get(path)
        out: dict[str, Decimal] = {}
        if not isinstance(payload, dict):
            return out
        results = payload.get("results")
        if not isinstance(results, list):
            return out
        for row in results:
            if not isinstance(row, dict):
                continue
            symbol = row.get("symbol")
            if not symbol:
                continue
            bid = _to_decimal(row.get("bid"))
            ask = _to_decimal(row.get("ask"))
            if bid is None and ask is None:
                continue
            if bid is not None and ask is not None:
                mid = (bid + ask) / Decimal("2")
            else:
                mid = bid if bid is not None else ask
            if mid is not None and mid > 0:
                out[str(symbol).strip().upper()] = mid
        return out


def _to_decimal(value: Any) -> Optional[Decimal]:
    if value is None:
        return None
    try:
        return Decimal(str(value))
    except Exception:  # noqa: BLE001
        return None


def _money(value: Decimal) -> str:
    return str(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def _select_account_number(accounts: list[dict[str, Any]]) -> str:
    for row in accounts:
        status = str(row.get("status") or "").strip().lower()
        acct = str(row.get("account_number") or "").strip()
        if acct and (not status or status == "active"):
            return acct
    for row in accounts:
        acct = str(row.get("account_number") or "").strip()
        if acct:
            return acct
    return ""


def run_crypto_sync(api_key: str, private_key_base64: str) -> dict[str, Any]:
    client = RobinhoodCryptoTradingClient(api_key, private_key_base64)
    warnings: list[str] = []
    try:
        accounts = client.list_accounts()
        account_number = _select_account_number(accounts)
        if not account_number:
            return {
                "ok": False,
                "message": "No crypto trading account found for these API credentials.",
                "accounts": accounts,
                "warnings": warnings,
            }

        raw_holdings = client.list_holdings(account_number)
        symbols: list[str] = []
        for row in raw_holdings:
            asset = str(row.get("asset_code") or "").strip().upper()
            qty = _to_decimal(row.get("total_quantity"))
            if asset and qty is not None and qty > 0:
                symbols.append(f"{asset}-USD")

        quotes = client.best_bid_ask(symbols)

        holdings: list[dict[str, Any]] = []
        total_value = Decimal("0")
        for row in raw_holdings:
            asset = str(row.get("asset_code") or "").strip().upper()
            qty = _to_decimal(row.get("total_quantity"))
            if not asset or qty is None or qty <= 0:
                continue
            pair = f"{asset}-USD"
            unit_price = quotes.get(pair)
            market_value = Decimal("0")
            if unit_price is not None:
                market_value = (qty * unit_price).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
            else:
                warnings.append(f"No quote for {pair}; market value set to 0")
            total_value += market_value
            holdings.append(
                {
                    "symbol": asset,
                    "quantity": str(qty.normalize()),
                    "currentUnitPrice": _money(unit_price) if unit_price is not None else "0",
                    "marketValue": _money(market_value),
                    "costBasis": "0",
                    "averageBuyPrice": "0",
                    "unrealizedPnL": "0",
                    "unrealizedPnLPercent": "0",
                }
            )

        holdings.sort(key=lambda h: h.get("symbol", ""))

        return {
            "ok": True,
            "message": f"Synced {len(holdings)} crypto holding(s).",
            "account_number": account_number,
            "total_value": _money(total_value),
            "holdings": holdings,
            "accounts": accounts,
            "warnings": warnings,
        }
    finally:
        client.close()
