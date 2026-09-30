"""Individual ••••3370 YTD check — broker realized vs open mark."""

from __future__ import annotations

import logging
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any
from zoneinfo import ZoneInfo

from realized_pnl_service import last4, total_and_trades

LOGGER = logging.getLogger(__name__)

ACCOUNTS_TOOL = "get_accounts"
PORTFOLIO_TOOL = "get_portfolio"
REALIZED_TOOL = "get_realized_pnl"
POSITIONS_TOOL = "get_equity_positions"
TRADES_TOOL = "get_pnl_trade_history"
SUFFIX = "3370"
LABEL = "Individual"
TIMEZONE = "America/New_York"
ZONE = ZoneInfo(TIMEZONE)


def _decimal(value: Any) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        return None


def _money(value: Any) -> str | None:
    parsed = _decimal(value)
    return str(parsed) if parsed is not None else None


def parse_portfolio(payload: Any) -> dict[str, str | None]:
    data = payload.get("data") if isinstance(payload, dict) and isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return {}
    buying = data.get("buying_power")
    buying_power = None
    if isinstance(buying, dict):
        buying_power = _money(buying.get("buying_power"))
    elif buying is not None:
        buying_power = _money(buying)
    return {
        "account_value": _money(data.get("total_value")),
        "equity_value": _money(data.get("equity_value")),
        "cash": _money(data.get("cash")),
        "buying_power": buying_power,
    }


def parse_positions(payload: Any) -> list[dict[str, Any]]:
    data = payload.get("data") if isinstance(payload, dict) and isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return []
    rows = data.get("positions")
    if not isinstance(rows, list):
        return []
    out: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        symbol = str(row.get("symbol") or "").strip().upper()
        if not symbol:
            continue
        qty = _decimal(row.get("quantity"))
        avg = _decimal(row.get("average_buy_price"))
        out.append(
            {
                "symbol": symbol,
                "quantity": str(qty) if qty is not None else "0",
                "average_buy_price": str(avg) if avg is not None else None,
            }
        )
    return out


def parse_recent_closes(payload: Any, limit: int = 12) -> list[dict[str, Any]]:
    data = payload.get("data") if isinstance(payload, dict) and isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return []
    trades = data.get("trades")
    if not isinstance(trades, list):
        return []
    out: list[dict[str, Any]] = []
    for row in trades[:limit]:
        if not isinstance(row, dict):
            continue
        symbol = str(row.get("symbol") or "").strip().upper()
        if not symbol:
            continue
        out.append(
            {
                "timestamp": str(row.get("timestamp") or ""),
                "symbol": symbol,
                "side": str(row.get("side") or ""),
                "quantity": str(row.get("quantity") or ""),
                "price": _money(row.get("price")),
                "realized": _money(row.get("realized_gain")),
            }
        )
    return out


def _as_of(year: int, as_of: str | None) -> date:
    today = datetime.now(ZONE).date()
    if as_of:
        return date.fromisoformat(as_of)
    return today if year == today.year else date(year, 12, 31)


def run_ytd_check(access_token: str, year: int, as_of: str | None = None) -> dict[str, Any]:
    end = _as_of(year, as_of)
    start = date(year, 1, 1)
    prior = end - timedelta(days=1)
    warnings: list[str] = []
    from mcp_client import RobinhoodMcpClient
    from mcp_tool_utils import extract_accounts, list_tool_names, parse_tool_payload

    client = RobinhoodMcpClient(access_token=access_token)
    portfolio: dict[str, str | None] = {}
    positions: list[dict[str, Any]] = []
    closes: list[dict[str, Any]] = []
    realized: dict[str, str | None] = {
        "all": None,
        "equity": None,
        "option": None,
        "crypto": None,
        "calendar_day": None,
        "app_day": None,
    }
    app_day_trades = 0
    try:
        client.initialize()
        tools = list_tool_names(client.list_tools())
        if ACCOUNTS_TOOL not in tools or REALIZED_TOOL not in tools:
            warnings.append("Robinhood realized P&L tools unavailable")
            return _result(year, start, end, portfolio, realized, app_day_trades, positions, closes, warnings)

        listed = extract_accounts(client.call_tool(ACCOUNTS_TOOL, {}))
        number = ""
        for acct in listed:
            candidate = str(acct.get("rhs_account_number") or acct.get("account_number") or "").strip()
            if last4(candidate) == SUFFIX:
                number = candidate
                break
        if not number:
            warnings.append("Individual ••••3370 was not in the account list")
            return _result(year, start, end, portfolio, realized, app_day_trades, positions, closes, warnings)

        if PORTFOLIO_TOOL in tools:
            portfolio = parse_portfolio(
                parse_tool_payload(client.call_tool(PORTFOLIO_TOOL, {"account_number": number}))
            )

        def fetch_realized(start_s: str, end_s: str, asset_classes: list[str] | None = None) -> tuple[Decimal | None, int]:
            args: dict[str, Any] = {
                "account_number": number,
                "start_date": start_s,
                "end_date": end_s,
                "timezone": TIMEZONE,
            }
            if asset_classes:
                args["asset_classes"] = asset_classes
            payload = parse_tool_payload(client.call_tool(REALIZED_TOOL, args))
            if isinstance(payload, dict) and payload.get("error"):
                warnings.append(str(payload.get("error")))
                return None, 0
            return total_and_trades(payload)

        ytd_start, ytd_end = start.isoformat(), end.isoformat()
        total, _ = fetch_realized(ytd_start, ytd_end)
        realized["all"] = str(total) if total is not None else None
        for key, classes in (("equity", ["equity"]), ("option", ["option"]), ("crypto", ["crypto"])):
            part, _ = fetch_realized(ytd_start, ytd_end, classes)
            realized[key] = str(part) if part is not None else None
        day_total, _ = fetch_realized(end.isoformat(), end.isoformat())
        realized["calendar_day"] = str(day_total) if day_total is not None else None
        app_total, app_trades = fetch_realized(prior.isoformat(), end.isoformat())
        realized["app_day"] = str(app_total) if app_total is not None else None
        app_day_trades = app_trades

        if POSITIONS_TOOL in tools:
            positions = parse_positions(
                parse_tool_payload(client.call_tool(POSITIONS_TOOL, {"account_number": number}))
            )
        if TRADES_TOOL in tools:
            closes = parse_recent_closes(
                parse_tool_payload(client.call_tool(TRADES_TOOL, {"account_number": number, "span": "week"}))
            )
    except Exception as exc:  # noqa: BLE001
        LOGGER.warning("ytd check failed: %s", exc)
        warnings.append(str(exc))
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass
    return _result(year, start, end, portfolio, realized, app_day_trades, positions, closes, warnings)


def _result(
    year: int,
    start: date,
    end: date,
    portfolio: dict[str, str | None],
    realized: dict[str, str | None],
    app_day_trades: int,
    positions: list[dict[str, Any]],
    closes: list[dict[str, Any]],
    warnings: list[str],
) -> dict[str, Any]:
    return {
        "account_suffix": SUFFIX,
        "account_label": LABEL,
        "year": year,
        "start_date": start.isoformat(),
        "as_of": end.isoformat(),
        "timezone": TIMEZONE,
        "account_value": portfolio.get("account_value"),
        "equity_value": portfolio.get("equity_value"),
        "cash": portfolio.get("cash"),
        "buying_power": portfolio.get("buying_power"),
        "realized_ytd": realized.get("all"),
        "realized_equity": realized.get("equity"),
        "realized_option": realized.get("option"),
        "realized_crypto": realized.get("crypto"),
        "realized_calendar_day": realized.get("calendar_day"),
        "realized_app_day": realized.get("app_day"),
        "app_day_trades": app_day_trades,
        "positions": positions,
        "recent_closes": closes,
        "warnings": warnings,
    }
