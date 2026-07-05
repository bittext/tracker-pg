"""Robinhood Crypto Trading API sync (read-only accounts, holdings, quotes)."""

from __future__ import annotations

import base64
import datetime
import logging
from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Optional
from urllib.parse import urlencode

import httpx
from nacl.signing import SigningKey

LOGGER = logging.getLogger(__name__)

BASE_URL = "https://trading.robinhood.com"


class RobinhoodCryptoTradingClient:
    def __init__(self, api_key: str, private_key_base64: str) -> None:
        self.api_key = api_key.strip()
        seed = base64.b64decode(private_key_base64.strip())
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
