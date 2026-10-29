"""Robinhood Predict (event contracts) — closed trades and open mark.

Kept separate from community-sentiment Predicts. Uses MCP get_pnl_trade_history
and get_portfolio; event contracts currently arrive with a blank symbol/side.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any

from realized_pnl_service import last4

LOGGER = logging.getLogger(__name__)

ACCOUNTS_TOOL = "get_accounts"
PORTFOLIO_TOOL = "get_portfolio"
TRADES_TOOL = "get_pnl_trade_history"
MAX_PAGES = 20
SKIP_STATES = {"inactive", "closed", "disabled"}
SKIP_TYPES = ("ira", "roth")


def _decimal(value: Any) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        return None


def is_event_trade(row: dict[str, Any]) -> bool:
    if not isinstance(row, dict):
        return False
    asset = str(
        row.get("asset_type") or row.get("assetType") or row.get("instrument_type") or ""
    ).lower()
    if "event" in asset or asset in {"prediction", "predict"}:
        return True
    symbol = str(row.get("symbol") or "").strip()
    side = str(row.get("side") or "").strip()
    return symbol == "" and side == ""


def parse_event_closes(payload: Any) -> tuple[list[dict[str, Any]], str]:
    data = payload.get("data") if isinstance(payload, dict) and isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return [], ""
    trades = data.get("trades")
    if not isinstance(trades, list):
        return [], str(data.get("next_cursor") or "")
    out: list[dict[str, Any]] = []
    for row in trades:
        if not is_event_trade(row):
            continue
        qty = _decimal(row.get("quantity"))
        price = _decimal(row.get("price"))
        realized = _decimal(row.get("realized_gain"))
        parsed = {
            "timestamp": str(row.get("timestamp") or ""),
            "quantity": str(qty) if qty is not None else "0",
            "price": str(price) if price is not None else "0",
            "realized": str(realized) if realized is not None else "0",
        }
        label = history_label(row)
        if label:
            parsed["label"] = label
        out.append(parsed)
    return out, str(data.get("next_cursor") or "")


def history_label(row: dict[str, Any]) -> str:
    """Market name and side when the history row names them."""
    name = ""
    for key in ("description", "name", "title", "instrument", "symbol"):
        value = str(row.get(key) or "").strip()
        if value:
            name = value
            break
    side = str(row.get("side") or "").strip()
    parts = [part for part in (name, side) if part]
    return " · ".join(parts)[:240]


def collapse_cumulative_closes(closes: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Keep one Robinhood sale when the feed repeats it as the filled size grows.

    A later row replaces an earlier one when it has the same price, a larger
    quantity, about the same gain per contract, and arrives within two minutes.
    Separate fills at different prices stay.
    """
    ordered = sorted(enumerate(closes), key=lambda item: (str(item[1].get("timestamp") or ""), item[0]))
    drop: set[int] = set()
    for pos, (idx, earlier) in enumerate(ordered):
        for _, later in ordered[pos + 1 :]:
            if not _within_two_minutes(earlier.get("timestamp"), later.get("timestamp")):
                break
            if _supersedes(later, earlier):
                drop.add(idx)
                break
    return [row for i, row in enumerate(closes) if i not in drop]


def _within_two_minutes(earlier: Any, later: Any) -> bool:
    start = _parse_time(earlier)
    end = _parse_time(later)
    if start is None or end is None:
        return False
    return timedelta(0) <= end - start <= timedelta(minutes=2)


def _parse_time(value: Any) -> datetime | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return None


def _supersedes(later: dict[str, Any], earlier: dict[str, Any]) -> bool:
    later_qty = _decimal(later.get("quantity"))
    earlier_qty = _decimal(earlier.get("quantity"))
    later_price = _decimal(later.get("price"))
    earlier_price = _decimal(earlier.get("price"))
    later_realized = _decimal(later.get("realized"))
    earlier_realized = _decimal(earlier.get("realized"))
    if None in (later_qty, earlier_qty, later_price, earlier_price, later_realized, earlier_realized):
        return False
    assert later_qty is not None and earlier_qty is not None
    assert later_price is not None and earlier_price is not None
    assert later_realized is not None and earlier_realized is not None
    if later_qty <= earlier_qty or earlier_qty == 0 or later_qty == 0:
        return False
    if abs(later_price - earlier_price) > Decimal("0.0001"):
        return False
    if earlier_realized != 0 and later_realized != 0 and (later_realized > 0) != (earlier_realized > 0):
        return False
    unit_earlier = earlier_realized / earlier_qty
    unit_later = later_realized / later_qty
    return abs(unit_later - unit_earlier) <= Decimal("0.02")


def parse_open_value(payload: Any) -> Decimal | None:
    data = payload.get("data") if isinstance(payload, dict) and isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return None
    return _decimal(data.get("event_contracts_value"))


def parse_open_cost(payload: Any) -> Decimal | None:
    """Cash put into open event contracts, when the portfolio names it."""
    data = payload.get("data") if isinstance(payload, dict) and isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return None
    for key in ("event_contracts_cost", "event_contracts_cost_basis", "event_contracts_invested"):
        value = _decimal(data.get(key))
        if value is not None:
            return value
    return None


def run_predict_markets(access_token: str) -> dict[str, Any]:
    warnings: list[str] = []
    accounts_out: list[dict[str, Any]] = []
    open_total = Decimal("0")
    cost_total = Decimal("0")
    saw_open = False
    saw_cost = False
    from mcp_client import RobinhoodMcpClient
    from mcp_tool_utils import extract_accounts, list_tool_names, parse_tool_payload

    client = RobinhoodMcpClient(access_token=access_token)
    try:
        client.initialize()
        tools = list_tool_names(client.list_tools())
        if ACCOUNTS_TOOL not in tools:
            warnings.append("get_accounts unavailable")
            return _result(accounts_out, open_total if saw_open else None, cost_total if saw_cost else None, warnings)
        if TRADES_TOOL not in tools:
            warnings.append("get_pnl_trade_history unavailable")
            return _result(accounts_out, open_total if saw_open else None, cost_total if saw_cost else None, warnings)

        listed = extract_accounts(client.call_tool(ACCOUNTS_TOOL, {}))
        targets: list[tuple[str, str, str]] = []
        seen: set[str] = set()
        for acct in listed:
            number = str(acct.get("rhs_account_number") or acct.get("account_number") or "").strip()
            suffix = last4(number)
            if not number or not suffix or number in seen:
                continue
            state = str(acct.get("state") or "").lower()
            if state in SKIP_STATES:
                continue
            btype = str(acct.get("brokerage_account_type") or "").lower()
            if any(part in btype for part in SKIP_TYPES):
                continue
            seen.add(number)
            nickname = str(acct.get("nickname") or "").strip()
            label = nickname or str(acct.get("brokerage_account_type") or suffix)
            targets.append((suffix, number, label))

        for suffix, number, label in targets:
            closes: list[dict[str, Any]] = []
            cursor = ""
            for _ in range(MAX_PAGES):
                args: dict[str, Any] = {"account_number": number, "span": "all"}
                if cursor:
                    args["cursor"] = cursor
                payload = parse_tool_payload(client.call_tool(TRADES_TOOL, args))
                if isinstance(payload, dict) and payload.get("error"):
                    warnings.append(f"{suffix}: {payload.get('error')}")
                    break
                page, cursor = parse_event_closes(payload)
                closes.extend(page)
                if not cursor:
                    break
            closes = collapse_cumulative_closes(closes)

            open_value = None
            open_cost = None
            if PORTFOLIO_TOOL in tools:
                portfolio = parse_tool_payload(client.call_tool(PORTFOLIO_TOOL, {"account_number": number}))
                open_value = parse_open_value(portfolio)
                open_cost = parse_open_cost(portfolio)
                if open_value is not None:
                    open_total += open_value
                    saw_open = True
                if open_cost is not None:
                    cost_total += open_cost
                    saw_cost = True

            realized = Decimal("0")
            for row in closes:
                part = _decimal(row.get("realized"))
                if part is not None:
                    realized += part
            accounts_out.append(
                {
                    "suffix": suffix,
                    "label": label,
                    "open_value": str(open_value) if open_value is not None else None,
                    "open_cost": str(open_cost) if open_cost is not None else None,
                    "realized_all": str(realized),
                    "closes": closes,
                }
            )
    except Exception as exc:  # noqa: BLE001
        LOGGER.warning("predict markets failed: %s", exc)
        warnings.append(str(exc))
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass
    return _result(
        accounts_out,
        open_total if saw_open else None,
        cost_total if saw_cost else None,
        warnings,
    )


def _result(
    accounts: list[dict[str, Any]],
    open_value: Decimal | None,
    open_cost: Decimal | None,
    warnings: list[str],
) -> dict[str, Any]:
    return {
        "open_value": str(open_value) if open_value is not None else None,
        "open_cost": str(open_cost) if open_cost is not None else None,
        "accounts": accounts,
        "warnings": warnings,
    }
