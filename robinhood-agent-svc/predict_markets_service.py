"""Robinhood Predict (event contracts) — closed trades and open mark.

Kept separate from community-sentiment Predicts. Uses MCP get_pnl_trade_history
and get_portfolio; event contracts currently arrive with a blank symbol/side.
"""

from __future__ import annotations

import logging
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
        out.append(
            {
                "timestamp": str(row.get("timestamp") or ""),
                "quantity": str(qty) if qty is not None else "0",
                "price": str(price) if price is not None else "0",
                "realized": str(realized) if realized is not None else "0",
            }
        )
    return out, str(data.get("next_cursor") or "")


def parse_open_value(payload: Any) -> Decimal | None:
    data = payload.get("data") if isinstance(payload, dict) and isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return None
    return _decimal(data.get("event_contracts_value"))


def run_predict_markets(access_token: str) -> dict[str, Any]:
    warnings: list[str] = []
    accounts_out: list[dict[str, Any]] = []
    open_total = Decimal("0")
    saw_open = False
    from mcp_client import RobinhoodMcpClient
    from mcp_tool_utils import extract_accounts, list_tool_names, parse_tool_payload

    client = RobinhoodMcpClient(access_token=access_token)
    try:
        client.initialize()
        tools = list_tool_names(client.list_tools())
        if ACCOUNTS_TOOL not in tools:
            warnings.append("get_accounts unavailable")
            return _result(accounts_out, open_total if saw_open else None, warnings)
        if TRADES_TOOL not in tools:
            warnings.append("get_pnl_trade_history unavailable")
            return _result(accounts_out, open_total if saw_open else None, warnings)

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

            open_value = None
            if PORTFOLIO_TOOL in tools:
                portfolio = parse_tool_payload(client.call_tool(PORTFOLIO_TOOL, {"account_number": number}))
                open_value = parse_open_value(portfolio)
                if open_value is not None:
                    open_total += open_value
                    saw_open = True

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
    return _result(accounts_out, open_total if saw_open else None, warnings)


def _result(
    accounts: list[dict[str, Any]],
    open_value: Decimal | None,
    warnings: list[str],
) -> dict[str, Any]:
    return {
        "open_value": str(open_value) if open_value is not None else None,
        "accounts": accounts,
        "warnings": warnings,
    }
