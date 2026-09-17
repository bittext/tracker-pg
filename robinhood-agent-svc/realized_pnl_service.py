"""Robinhood MCP get_realized_pnl — calendar-window closed P&L for Tax desk accounts."""

from __future__ import annotations

import logging
from decimal import Decimal, InvalidOperation
from typing import Any

LOGGER = logging.getLogger(__name__)

ACCOUNTS_TOOL = "get_accounts"
REALIZED_TOOL = "get_realized_pnl"
TAX_DESK_SUFFIXES = ("3370", "3550", "8696")
TIMEZONE = "America/Chicago"
LABELS = {"3370": "Individual", "3550": "Agentic", "8696": "Ammu"}


def last4(account_number: str | None) -> str:
    digits = "".join(ch for ch in (account_number or "") if ch.isdigit())
    return digits[-4:] if len(digits) >= 4 else ""


def _decimal(value: Any) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        return None


def total_and_trades(payload: Any) -> tuple[Decimal | None, int]:
    """Read window total_returns; fall back to summing bucket realized_gain."""
    if not isinstance(payload, dict):
        return None, 0
    data = payload.get("data") if isinstance(payload.get("data"), dict) else payload
    if not isinstance(data, dict):
        return None, 0
    trades = 0
    bucket_sum = Decimal("0")
    saw_bucket = False
    points = data.get("data_points")
    if isinstance(points, list):
        for point in points:
            if not isinstance(point, dict):
                continue
            try:
                trades += int(point.get("number_of_trades") or 0)
            except (TypeError, ValueError):
                pass
            gain = _decimal(point.get("realized_gain"))
            if gain is not None:
                bucket_sum += gain
                saw_bucket = True
    total = _decimal(data.get("total_returns"))
    if total is None and saw_bucket:
        total = bucket_sum
    return total, trades


def run_realized_pnl(
    access_token: str,
    start_date: str,
    end_date: str,
    suffixes: list[str] | None = None,
) -> dict[str, Any]:
    wanted = {
        str(s).strip()[-4:]
        for s in (suffixes or TAX_DESK_SUFFIXES)
        if str(s).strip()
    }
    warnings: list[str] = []
    accounts_out: list[dict[str, Any]] = []
    from mcp_client import RobinhoodMcpClient
    from mcp_tool_utils import extract_accounts, list_tool_names, parse_tool_payload

    client = RobinhoodMcpClient(access_token=access_token)
    try:
        client.initialize()
        tool_names = list_tool_names(client.list_tools())
        if ACCOUNTS_TOOL not in tool_names:
            warnings.append("get_accounts unavailable")
            return _result(start_date, end_date, accounts_out, warnings)
        if REALIZED_TOOL not in tool_names:
            warnings.append("get_realized_pnl unavailable")
            return _result(start_date, end_date, accounts_out, warnings)

        listed = extract_accounts(client.call_tool(ACCOUNTS_TOOL, {}))
        targets: list[tuple[str, str]] = []
        seen: set[str] = set()
        for acct in listed:
            number = str(acct.get("rhs_account_number") or acct.get("account_number") or "").strip()
            suffix = last4(number)
            if not number or suffix not in wanted or number in seen:
                continue
            state = str(acct.get("state") or "").lower()
            if state in ("inactive", "closed", "disabled"):
                continue
            btype = str(acct.get("brokerage_account_type") or "").lower()
            if "ira" in btype or "roth" in btype:
                continue
            seen.add(number)
            targets.append((suffix, number))
        targets.sort(key=lambda t: list(TAX_DESK_SUFFIXES).index(t[0]) if t[0] in TAX_DESK_SUFFIXES else 99)

        for suffix, number in targets:
            raw = client.call_tool(
                REALIZED_TOOL,
                {
                    "account_number": number,
                    "start_date": start_date,
                    "end_date": end_date,
                    "timezone": TIMEZONE,
                },
            )
            payload = parse_tool_payload(raw)
            if isinstance(payload, dict) and payload.get("error"):
                warnings.append(f"{suffix}: {payload.get('error')}")
                continue
            total, trades = total_and_trades(payload)
            accounts_out.append(
                {
                    "suffix": suffix,
                    "label": LABELS.get(suffix, suffix),
                    "account_number_last4": suffix,
                    "realized": str(total) if total is not None else "0",
                    "closing_trades": trades,
                }
            )
    except Exception as exc:  # noqa: BLE001
        LOGGER.warning("realized pnl failed: %s", exc)
        warnings.append(str(exc))
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass

    return _result(start_date, end_date, accounts_out, warnings)


def _result(
    start_date: str,
    end_date: str,
    accounts: list[dict[str, Any]],
    warnings: list[str],
) -> dict[str, Any]:
    total = Decimal("0")
    for row in accounts:
        part = _decimal(row.get("realized"))
        if part is not None:
            total += part
    return {
        "start_date": start_date,
        "end_date": end_date,
        "timezone": TIMEZONE,
        "total_realized": str(total),
        "accounts": accounts,
        "warnings": warnings,
    }
