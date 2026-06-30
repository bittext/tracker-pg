"""Live Robinhood MCP quotes for holdings market-value refresh."""

from __future__ import annotations

import logging
from typing import Any

from mcp_client import RobinhoodMcpClient
from mcp_tool_utils import list_tool_names, parse_tool_payload
from sync_service import EQUITY_QUOTES_TOOL, QUOTE_BATCH_SIZE, _price_from_quote, _quotes_by_symbol, _to_float

LOGGER = logging.getLogger(__name__)

OPTION_QUOTES_TOOL = "get_option_quotes"
OPTION_QUOTE_BATCH_SIZE = 20


def _option_marks_by_instrument(payload: Any) -> dict[str, float]:
    """Extract instrument_id → mark_price (per share) from get_option_quotes."""
    marks: dict[str, float] = {}
    candidates: list[Any] = [payload]
    if isinstance(payload, dict):
        data = payload.get("data", payload)
        candidates = [data, payload]
        if isinstance(data, dict):
            for key in ("results", "quotes", "items"):
                items = data.get(key)
                if isinstance(items, list):
                    candidates = items
                    break

    rows: list[dict[str, Any]] = []
    for candidate in candidates:
        if isinstance(candidate, list):
            rows.extend(row for row in candidate if isinstance(row, dict))

    for row in rows:
        quote = row.get("quote") if isinstance(row.get("quote"), dict) else row
        if not isinstance(quote, dict):
            continue
        instrument_id = quote.get("instrument_id") or quote.get("id")
        if not instrument_id:
            continue
        mark = _to_float(
            quote.get("mark_price")
            or quote.get("adjusted_mark_price")
            or quote.get("last_trade_price")
            or quote.get("bid_price")
        )
        if mark is not None and mark > 0:
            marks[str(instrument_id)] = mark
    return marks


def _batched(values: list[str], size: int) -> list[list[str]]:
    if not values:
        return []
    return [values[i : i + size] for i in range(0, len(values), size)]


def run_quotes(
    access_token: str,
    *,
    symbols: list[str] | None = None,
    option_instrument_ids: list[str] | None = None,
) -> dict[str, Any]:
    """Fetch live equity and option marks from Robinhood MCP."""
    equity_symbols = sorted({str(s).strip().upper() for s in (symbols or []) if s and str(s).strip()})
    option_ids = sorted({str(i).strip() for i in (option_instrument_ids or []) if i and str(i).strip()})

    client = RobinhoodMcpClient(access_token=access_token)
    tool_names = list_tool_names(client)
    warnings: list[str] = []
    equity_prices: dict[str, float] = {}
    option_marks: dict[str, float] = {}

    if equity_symbols and EQUITY_QUOTES_TOOL not in tool_names:
        warnings.append("get_equity_quotes unavailable")
    elif equity_symbols:
        for batch in _batched(equity_symbols, QUOTE_BATCH_SIZE):
            try:
                raw = client.call_tool(EQUITY_QUOTES_TOOL, {"symbols": batch})
                equity_prices.update(_quotes_by_symbol(parse_tool_payload(raw)))
            except Exception as exc:  # noqa: BLE001
                LOGGER.warning("get_equity_quotes failed for %s: %s", batch, exc)
                warnings.append(f"Equity quotes failed for {', '.join(batch)}: {exc}")

    if option_ids and OPTION_QUOTES_TOOL not in tool_names:
        warnings.append("get_option_quotes unavailable")
    elif option_ids:
        for batch in _batched(option_ids, OPTION_QUOTE_BATCH_SIZE):
            try:
                raw = client.call_tool(OPTION_QUOTES_TOOL, {"instrument_ids": batch})
                option_marks.update(_option_marks_by_instrument(parse_tool_payload(raw)))
            except Exception as exc:  # noqa: BLE001
                LOGGER.warning("get_option_quotes failed for %s: %s", batch, exc)
                warnings.append(f"Option quotes failed for {len(batch)} contract(s): {exc}")

    return {
        "equity_prices": equity_prices,
        "option_marks": option_marks,
        "warnings": warnings,
    }
