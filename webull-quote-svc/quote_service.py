"""Live equity and option snapshots via Webull OpenAPI."""

from __future__ import annotations

import logging
import os
from functools import lru_cache
from typing import Any

from occ_symbol import build_occ_symbol
from webull.core.client import ApiClient
from webull.data.common.category import Category
from webull.data.data_client import DataClient

LOGGER = logging.getLogger(__name__)

EQUITY_BATCH = 100
OPTION_BATCH = 20


def _to_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        out = float(value)
    except (TypeError, ValueError):
        return None
    return out if out > 0 else None


def _price_from_row(row: dict[str, Any]) -> float | None:
    for key in (
        "trade_price",
        "last_price",
        "close",
        "mark_price",
        "price",
        "latest_price",
        "last_trade_price",
        "bid_price",
        "ask_price",
    ):
        val = _to_float(row.get(key))
        if val is not None:
            return val
    return None


def _rows_from_payload(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [row for row in payload if isinstance(row, dict)]
    if not isinstance(payload, dict):
        return []
    for key in ("data", "result", "results", "items", "snapshots"):
        node = payload.get(key)
        if isinstance(node, list):
            return [row for row in node if isinstance(row, dict)]
        if isinstance(node, dict):
            for inner in ("data", "results", "items", "snapshots"):
                items = node.get(inner)
                if isinstance(items, list):
                    return [row for row in items if isinstance(row, dict)]
    return []


def _symbol_from_row(row: dict[str, Any]) -> str | None:
    for key in ("symbol", "ticker", "dis_symbol"):
        text = row.get(key)
        if isinstance(text, str) and text.strip():
            return text.strip().upper()
    return None


@lru_cache(maxsize=1)
def _data_client() -> DataClient:
    user_id = (
        os.environ.get("WEBULL_USER_ID", "").strip()
        or os.environ.get("WEBULL_APP_KEY", "").strip()  # legacy alias
    )
    app_secret = (
        os.environ.get("WEBULL_APP_SECRET", "").strip()
        or os.environ.get("WEBULL_APP_KEY_SECRET", "").strip()
    )
    # OpenAPI signing key when separate from user id; otherwise user id is the AK.
    app_key = os.environ.get("WEBULL_APP_KEY_ID", "").strip() or user_id
    if not user_id or not app_secret:
        raise RuntimeError("WEBULL_USER_ID and WEBULL_APP_SECRET are required")
    region = os.environ.get("WEBULL_REGION", "us").strip() or "us"
    api_host = os.environ.get("WEBULL_API_HOST", "api.webull.com").strip() or "api.webull.com"
    api_client = ApiClient(app_key, app_secret, region, user_id=user_id)
    api_client.add_endpoint(region, api_host)
    return DataClient(api_client)


def _batched(values: list[str], size: int) -> list[list[str]]:
    if not values:
        return []
    return [values[i : i + size] for i in range(0, len(values), size)]


def _fetch_equity_snapshots(symbols: list[str]) -> tuple[dict[str, float], list[str]]:
    prices: dict[str, float] = {}
    warnings: list[str] = []
    if not symbols:
        return prices, warnings
    client = _data_client()
    for batch in _batched(symbols, EQUITY_BATCH):
        try:
            response = client.market_data.get_snapshot(batch, Category.US_STOCK.name)
            if response.status_code != 200:
                warnings.append(f"Webull equity snapshot HTTP {response.status_code} for {batch}")
                continue
            for row in _rows_from_payload(response.json()):
                symbol = _symbol_from_row(row)
                price = _price_from_row(row)
                if symbol and price is not None:
                    prices[symbol] = price
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("Webull equity snapshot failed for %s: %s", batch, exc)
            warnings.append(f"Equity snapshot failed for {', '.join(batch)}: {exc}")
    return prices, warnings


def _fetch_option_snapshots(
    options: list[dict[str, Any]],
) -> tuple[dict[str, float], list[str]]:
    marks: dict[str, float] = {}
    warnings: list[str] = []
    if not options:
        return marks, warnings

    occ_by_instrument: dict[str, str] = {}
    for opt in options:
        instrument_id = str(opt.get("instrument_id") or "").strip()
        if not instrument_id:
            continue
        try:
            occ = build_occ_symbol(
                str(opt.get("symbol") or ""),
                str(opt.get("expiration") or ""),
                opt.get("strike"),
                str(opt.get("option_type") or ""),
            )
        except ValueError as exc:
            warnings.append(f"Skip option {instrument_id}: {exc}")
            continue
        occ_by_instrument[instrument_id] = occ

    if not occ_by_instrument:
        return marks, warnings

    client = _data_client()
    occ_symbols = list(dict.fromkeys(occ_by_instrument.values()))
    occ_price: dict[str, float] = {}
    for batch in _batched(occ_symbols, OPTION_BATCH):
        try:
            response = client.option_market_data.get_option_snapshot(batch, Category.US_OPTION.name)
            if response.status_code != 200:
                warnings.append(f"Webull option snapshot HTTP {response.status_code}")
                continue
            for row in _rows_from_payload(response.json()):
                symbol = _symbol_from_row(row)
                price = _price_from_row(row)
                if symbol and price is not None:
                    occ_price[symbol.upper()] = price
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("Webull option snapshot failed for %s: %s", batch, exc)
            warnings.append(f"Option snapshot failed for {len(batch)} contract(s): {exc}")

    for instrument_id, occ in occ_by_instrument.items():
        price = occ_price.get(occ.upper())
        if price is not None:
            marks[instrument_id] = price

    return marks, warnings


def run_quotes(
    *,
    symbols: list[str] | None = None,
    options: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    equity_symbols = sorted({str(s).strip().upper() for s in (symbols or []) if s and str(s).strip()})
    option_rows = [row for row in (options or []) if isinstance(row, dict)]

    equity_prices, equity_warnings = _fetch_equity_snapshots(equity_symbols)
    option_marks, option_warnings = _fetch_option_snapshots(option_rows)

    return {
        "ok": True,
        "equity_prices": equity_prices,
        "option_marks": option_marks,
        "warnings": equity_warnings + option_warnings,
    }
