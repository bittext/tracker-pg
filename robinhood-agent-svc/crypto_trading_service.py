"""Robinhood Crypto Trading API sync and orders."""

from __future__ import annotations

import base64
import datetime
import json
import logging
import uuid
from collections import defaultdict, deque
from decimal import Decimal, ROUND_DOWN, ROUND_HALF_UP
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

        resolved_qty = asset_quantity
        if not resolved_qty:
            if order_side == "buy":
                resolved_qty = self._quote_to_buy_quantity(pair, quote_amount)
            else:
                raise ValueError("asset_quantity is required for sell market orders via API")

        market_config = {"asset_quantity": _format_asset_quantity(Decimal(str(resolved_qty)))}

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
            result.setdefault("asset_quantity", market_config["asset_quantity"])
        return result if isinstance(result, dict) else {"result": result}

    def _quote_to_buy_quantity(self, pair: str, quote_amount: str) -> str:
        ask = self._ask_price(pair)
        if ask is None or ask <= 0:
            raise ValueError(f"No ask price available for {pair}")
        notional = Decimal(str(quote_amount))
        if notional <= 0:
            raise ValueError("quote_amount must be positive")
        qty = notional / ask
        formatted = _format_asset_quantity(qty)
        if Decimal(formatted) <= 0:
            raise ValueError(f"quote_amount {quote_amount} is too small for current {pair} price")
        return formatted

    def _ask_price(self, pair: str) -> Optional[Decimal]:
        base_path = "/api/v2/crypto/marketdata/best_bid_ask/"
        path = f"{base_path}?{urlencode([('symbol', pair)])}"
        payload = self._get(path)
        if not isinstance(payload, dict):
            return None
        results = payload.get("results")
        if not isinstance(results, list):
            return None
        for row in results:
            if not isinstance(row, dict):
                continue
            if str(row.get("symbol") or "").strip().upper() != pair:
                continue
            ask = _to_decimal(row.get("ask"))
            if ask is not None and ask > 0:
                return ask
            bid = _to_decimal(row.get("bid"))
            if bid is not None and bid > 0:
                return bid
        return None

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

    def list_orders(self, account_number: str) -> list[dict[str, Any]]:
        all_rows: list[dict[str, Any]] = []
        base_path = "/api/v2/crypto/trading/orders/"
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


DEFAULT_TAKER_FEE_RATE = Decimal("0.0095")


def _money(value: Decimal) -> str:
    return str(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def _price(value: Decimal, places: int = 12) -> str:
    quant = Decimal("1").scaleb(-places)
    text = format(value.quantize(quant, rounding=ROUND_HALF_UP), "f")
    return text.rstrip("0").rstrip(".") or "0"


def _format_asset_quantity(qty: Decimal) -> str:
    # Robinhood API: round to nearest 0.000001 for market orders.
    q = qty.quantize(Decimal("0.000001"), rounding=ROUND_DOWN)
    text = f"{q:.6f}".rstrip("0").rstrip(".")
    return text if text else "0"


def _order_symbol(row: dict[str, Any]) -> str:
    raw = str(row.get("currency_code") or row.get("symbol") or "").strip().upper()
    if raw.endswith("-USD"):
        return raw[:-4]
    if raw.endswith("USD") and len(raw) > 3:
        return raw[:-3]
    return raw


def _order_quantity(row: dict[str, Any]) -> Optional[Decimal]:
    for key in ("filled_asset_quantity", "cumulative_quantity", "quantity"):
        qty = _to_decimal(row.get(key))
        if qty is not None and qty > 0:
            return qty
    executions = row.get("executions")
    if isinstance(executions, list):
        total = Decimal("0")
        for execution in executions:
            if isinstance(execution, dict):
                piece = _to_decimal(execution.get("quantity"))
                if piece is not None:
                    total += piece
        if total > 0:
            return total
    return None


def _order_fee_amount(row: dict[str, Any]) -> Decimal:
    charged = _to_decimal(row.get("fee_charged"))
    if charged is not None and charged > 0:
        return charged
    fees = row.get("fees")
    if isinstance(fees, list):
        for fee in fees:
            if not isinstance(fee, dict):
                continue
            data = fee.get("fee_data")
            if isinstance(data, dict):
                amount = _to_decimal(data.get("fee_amount"))
                if amount is not None:
                    return amount
    direct = _to_decimal(row.get("fee"))
    return direct if direct is not None else Decimal("0")


def _order_fee_rate(row: dict[str, Any]) -> Optional[Decimal]:
    fees = row.get("fees")
    if isinstance(fees, list):
        for fee in fees:
            if not isinstance(fee, dict):
                continue
            data = fee.get("fee_data")
            if isinstance(data, dict):
                ratio = _to_decimal(data.get("fee_ratio"))
                if ratio is not None and ratio > 0:
                    return ratio
    rate = _to_decimal(row.get("fee_rate"))
    if rate is not None and rate > 0:
        return rate
    fee = _order_fee_amount(row)
    qty = _order_quantity(row)
    avg = _to_decimal(row.get("average_price"))
    if fee > 0 and qty and avg and qty * avg > 0:
        return fee / (qty * avg)
    return None


def _filled_buy_cost(row: dict[str, Any], qty: Decimal) -> tuple[Decimal, Decimal]:
    """Return (cash outlay including fee, fee) for a filled buy."""
    notional = _to_decimal(row.get("rounded_executed_notional")) or _to_decimal(
        row.get("total_executed_notional")
    )
    if notional is None:
        avg = _to_decimal(row.get("average_price"))
        notional = (avg * qty) if avg is not None else Decimal("0")
    with_fee = _to_decimal(row.get("rounded_executed_notional_with_fee"))
    fee = _order_fee_amount(row)
    if with_fee is None:
        with_fee = notional + fee
    elif fee == 0 and with_fee > notional:
        fee = with_fee - notional
    return with_fee, fee


def lots_from_orders(orders: list[dict[str, Any]]) -> dict[str, dict[str, Decimal]]:
    """FIFO remaining lots per symbol from filled orders. Cost includes buy fees."""
    filled = [
        row
        for row in orders
        if str(row.get("state") or "").strip().lower() == "filled"
        and _order_symbol(row)
        and str(row.get("side") or "").strip().lower() in {"buy", "sell"}
    ]
    filled.sort(key=lambda row: str(row.get("created_at") or ""))
    books: dict[str, deque[dict[str, Decimal]]] = defaultdict(deque)
    last_fee_rate: dict[str, Decimal] = {}
    lifetime_fees: dict[str, Decimal] = defaultdict(lambda: Decimal("0"))

    for row in filled:
        symbol = _order_symbol(row)
        side = str(row.get("side") or "").strip().lower()
        qty = _order_quantity(row)
        if qty is None or qty <= 0:
            continue
        rate = _order_fee_rate(row)
        if rate is not None:
            last_fee_rate[symbol] = rate
        if side == "buy":
            cost, fee = _filled_buy_cost(row, qty)
            lifetime_fees[symbol] += fee
            books[symbol].append({"qty": qty, "cost": cost, "fee": fee})
            continue
        sell_fee = _order_fee_amount(row)
        lifetime_fees[symbol] += sell_fee
        remaining = qty
        lots = books[symbol]
        while remaining > 0 and lots:
            lot = lots[0]
            take = min(lot["qty"], remaining)
            if take == lot["qty"]:
                lots.popleft()
            else:
                share = take / lot["qty"]
                lot["qty"] -= take
                lot["cost"] -= lot["cost"] * share
                lot["fee"] -= lot["fee"] * share
            remaining -= take

    out: dict[str, dict[str, Decimal]] = {}
    for symbol, lots in books.items():
        qty = sum((lot["qty"] for lot in lots), Decimal("0"))
        cost = sum((lot["cost"] for lot in lots), Decimal("0"))
        buy_fees = sum((lot["fee"] for lot in lots), Decimal("0"))
        if qty <= 0:
            continue
        cost = cost.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        buy_fees = buy_fees.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        out[symbol] = {
            "quantity": qty,
            "costBasis": cost,
            "buyFees": buy_fees,
            "averageBuyPrice": cost / qty,
            "sellFeeRate": last_fee_rate.get(symbol, DEFAULT_TAKER_FEE_RATE),
            "lifetimeFees": lifetime_fees.get(symbol, Decimal("0")).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            ),
        }
    return out


def _holdings_from_rows(
    raw_holdings: list[dict[str, Any]],
    quotes: dict[str, Decimal],
    lots: dict[str, dict[str, Decimal]] | None = None,
) -> tuple[list[dict[str, Any]], Decimal, list[str]]:
    holdings: list[dict[str, Any]] = []
    total_value = Decimal("0")
    warnings: list[str] = []
    lots = lots or {}
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
        lot = lots.get(asset)
        cost = lot["costBasis"] if lot else Decimal("0")
        buy_fees = lot["buyFees"] if lot else Decimal("0")
        avg = lot["averageBuyPrice"] if lot else Decimal("0")
        fee_rate = lot["sellFeeRate"] if lot else DEFAULT_TAKER_FEE_RATE
        lifetime_fees = lot["lifetimeFees"] if lot else Decimal("0")
        if lot and lot["quantity"] > 0 and abs(lot["quantity"] - qty) / qty > Decimal("0.02"):
            # Holding qty drifted from FIFO (dust / transfer). Scale cost to live qty.
            scale = qty / lot["quantity"]
            cost *= scale
            buy_fees *= scale
            avg = cost / qty if qty else avg
        pnl = market_value - cost
        pnl_pct = (pnl / cost * Decimal("100")) if cost > 0 else Decimal("0")
        holdings.append(
            {
                "symbol": asset,
                "quantity": _price(qty, 8),
                "currentUnitPrice": _price(unit_price) if unit_price is not None else "0",
                "marketValue": _money(market_value),
                "costBasis": _money(cost),
                "averageBuyPrice": _price(avg),
                "buyFees": _money(buy_fees),
                "lifetimeFees": _money(lifetime_fees),
                "sellFeeRate": _price(fee_rate, 6),
                "unrealizedPnL": _money(pnl),
                "unrealizedPnLPercent": _price(pnl_pct, 4),
            }
        )
    holdings.sort(key=lambda h: h.get("symbol", ""))
    return holdings, total_value, warnings


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

        portfolios: list[dict[str, Any]] = []
        all_symbols: list[str] = []
        holdings_by_account: dict[str, list[dict[str, Any]]] = {}
        lots_by_account: dict[str, dict[str, dict[str, Decimal]]] = {}
        for acct in accounts:
            number = str(acct.get("account_number") or "").strip()
            status = str(acct.get("status") or "").strip().lower()
            if not number or (status and status not in {"active", ""}):
                continue
            raw_holdings = client.list_holdings(number)
            holdings_by_account[number] = raw_holdings
            for row in raw_holdings:
                asset = str(row.get("asset_code") or "").strip().upper()
                qty = _to_decimal(row.get("total_quantity"))
                if asset and qty is not None and qty > 0:
                    all_symbols.append(f"{asset}-USD")
            try:
                lots_by_account[number] = lots_from_orders(client.list_orders(number))
            except Exception as exc:  # noqa: BLE001
                warnings.append(f"Order history unavailable for cost basis on {number[-4:]}: {exc}")
                lots_by_account[number] = {}

        quotes = client.best_bid_ask(all_symbols)
        primary_holdings: list[dict[str, Any]] = []
        primary_total = Decimal("0")

        for number, raw_holdings in holdings_by_account.items():
            holdings, total_value, pair_warnings = _holdings_from_rows(
                raw_holdings, quotes, lots_by_account.get(number)
            )
            warnings.extend(pair_warnings)
            portfolios.append(
                {
                    "account_number": number,
                    "total_value": _money(total_value),
                    "holdings": holdings,
                }
            )
            if number == account_number:
                primary_holdings = holdings
                primary_total = total_value

        if not primary_holdings and portfolios:
            primary_holdings = portfolios[0]["holdings"]
            primary_total = Decimal(str(portfolios[0]["total_value"]))

        coin_count = sum(len(p["holdings"]) for p in portfolios)
        return {
            "ok": True,
            "message": f"Synced {coin_count} crypto holding(s) across {len(portfolios)} account(s).",
            "account_number": account_number,
            "total_value": _money(primary_total),
            "holdings": primary_holdings,
            "portfolios": portfolios,
            "accounts": accounts,
            "warnings": warnings,
        }
    finally:
        client.close()
