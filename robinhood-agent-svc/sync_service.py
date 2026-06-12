"""Read-only Robinhood MCP sync for tracker-pg Phase 1."""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

from mcp_client import RobinhoodMcpClient
from mcp_tool_utils import extract_accounts, parse_tool_payload, pick_probe_accounts

LOGGER = logging.getLogger(__name__)


def _positions_from_payload(payload: Any) -> list[dict[str, Any]]:
    """Best-effort extract position rows from get_equity_positions payload."""
    if not isinstance(payload, dict):
        return []
    data = payload.get("data", payload)
    if isinstance(data, dict):
        for key in ("positions", "equity_positions", "results", "items"):
            items = data.get(key)
            if isinstance(items, list):
                return [_normalize_position(row) for row in items if isinstance(row, dict)]
    if isinstance(data, list):
        return [_normalize_position(row) for row in data if isinstance(row, dict)]
    return []


def _normalize_position(row: dict[str, Any]) -> dict[str, Any]:
    instrument = row.get("instrument")
    symbol = row.get("symbol") or row.get("instrument_symbol")
    if not symbol and isinstance(instrument, dict):
        symbol = instrument.get("symbol")
    return {
        "symbol": symbol,
        "quantity": row.get("quantity") or row.get("shares") or row.get("qty"),
        "average_buy_price": row.get("average_buy_price")
        or row.get("average_price")
        or row.get("avg_cost"),
        "market_value": row.get("market_value") or row.get("equity") or row.get("value"),
    }


def run_sync(access_token: str, *, sync_default: bool = True) -> dict[str, Any]:
    client = RobinhoodMcpClient(access_token=access_token)
    started = datetime.now(timezone.utc).isoformat()
    try:
        client.initialize()
        raw_accounts = client.call_tool("get_accounts", {})
        accounts = extract_accounts(raw_accounts)
        picks = pick_probe_accounts(accounts)
        agentic = picks.get("agentic")
        default = picks.get("default") if sync_default else None

        targets: list[tuple[str, dict[str, Any]]] = []
        if agentic:
            targets.append(("agentic", agentic))
        if default and (not agentic or default.get("account_number") != agentic.get("account_number")):
            targets.append(("default", default))

        if not targets:
            raise RuntimeError("No accounts found from get_accounts")

        account_summaries: list[dict[str, Any]] = []
        all_positions: list[dict[str, Any]] = []
        portfolios: dict[str, Any] = {}

        for role, account in targets:
            acct_num = str(account["account_number"])
            portfolio_raw = client.call_tool("get_portfolio", {"account_number": acct_num})
            positions_raw = client.call_tool("get_equity_positions", {"account_number": acct_num})
            portfolio_data = parse_tool_payload(portfolio_raw)
            positions_data = parse_tool_payload(positions_raw)
            positions = _positions_from_payload(positions_data)
            for p in positions:
                p["account_number"] = acct_num
                p["account_role"] = role
            all_positions.extend(positions)
            portfolios[acct_num] = portfolio_data
            account_summaries.append(
                {
                    "role": role,
                    "account_number": acct_num,
                    "nickname": account.get("nickname"),
                    "brokerage_account_type": account.get("brokerage_account_type"),
                    "agentic_allowed": account.get("agentic_allowed"),
                    "is_default": account.get("is_default"),
                    "position_count": len(positions),
                }
            )

        agentic_account = agentic or {}
        return {
            "ok": True,
            "started_at": started,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "agentic_account_number": str(agentic_account.get("account_number", "")) or None,
            "agentic_nickname": agentic_account.get("nickname"),
            "accounts": account_summaries,
            "portfolios": portfolios,
            "positions": all_positions,
        }
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass
