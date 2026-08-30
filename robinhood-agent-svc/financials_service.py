"""Robinhood MCP get_financials — quarterly revenue / net income / margin."""

from __future__ import annotations

import logging
from typing import Any

LOGGER = logging.getLogger(__name__)

FINANCIALS_TOOL = "get_financials"
DEFAULT_LIMIT = 12


def _to_optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text or text.lower() == "null":
        return None
    return text


def _rows_from_payload(payload: Any, symbol: str) -> list[dict[str, Any]]:
    """Extract financial period dicts from a get_financials tools/call payload."""
    if not isinstance(payload, dict):
        return []
    data = payload.get("data") if isinstance(payload.get("data"), dict) else payload
    results = data.get("results") if isinstance(data, dict) else None
    if not isinstance(results, list):
        return []
    wanted = symbol.strip().upper()
    for entry in results:
        if not isinstance(entry, dict):
            continue
        entry_symbol = str(entry.get("symbol") or "").strip().upper()
        if wanted and entry_symbol and entry_symbol != wanted:
            continue
        rows = entry.get("financials")
        if isinstance(rows, list):
            return [row for row in rows if isinstance(row, dict)]
    return []


def normalize_financial_row(row: dict[str, Any]) -> dict[str, Any] | None:
    period_end = _to_optional_str(row.get("period_end_date"))
    if not period_end:
        return None
    year = row.get("fiscal_year")
    quarter = row.get("fiscal_quarter")
    try:
        fiscal_year = int(year) if year is not None else 0
    except (TypeError, ValueError):
        fiscal_year = 0
    try:
        fiscal_quarter = int(quarter) if quarter is not None else 0
    except (TypeError, ValueError):
        fiscal_quarter = 0
    return {
        "fiscal_year": fiscal_year,
        "fiscal_quarter": fiscal_quarter,
        "period_end_date": period_end,
        "revenue": _to_optional_str(row.get("revenue")),
        "gross_profit": _to_optional_str(row.get("gross_profit")),
        "net_income": _to_optional_str(row.get("net_income")),
        "net_margin": _to_optional_str(row.get("net_margin")),
    }


def run_financials(access_token: str, symbol: str, *, limit: int = DEFAULT_LIMIT) -> dict[str, Any]:
    ticker = (symbol or "").strip().upper()
    if not ticker:
        return {"symbol": "", "period": "quarterly", "financials": [], "warnings": ["symbol required"]}

    cap = max(1, min(int(limit or DEFAULT_LIMIT), 40))
    warnings: list[str] = []
    rows: list[dict[str, Any]] = []
    from mcp_client import RobinhoodMcpClient
    from mcp_tool_utils import list_tool_names, parse_tool_payload

    client = RobinhoodMcpClient(access_token=access_token)
    try:
        client.initialize()
        tool_names = list_tool_names(client.list_tools())
        if FINANCIALS_TOOL not in tool_names:
            warnings.append("get_financials unavailable")
        else:
            raw = client.call_tool(
                FINANCIALS_TOOL,
                {"symbols": [ticker], "period": "quarterly", "limit": cap},
            )
            for row in _rows_from_payload(parse_tool_payload(raw), ticker):
                normalized = normalize_financial_row(row)
                if normalized is not None:
                    rows.append(normalized)
    except Exception as exc:  # noqa: BLE001
        LOGGER.warning("get_financials failed for %s: %s", ticker, exc)
        warnings.append(f"get_financials failed for {ticker}: {exc}")
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass

    return {
        "symbol": ticker,
        "period": "quarterly",
        "financials": rows,
        "warnings": warnings,
    }
