"""Equity order review and placement via Robinhood MCP (Phase 2)."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from mcp_client import RobinhoodMcpClient
from mcp_tool_utils import extract_accounts, parse_tool_payload, pick_probe_accounts

REVIEW_TOOL = "review_equity_order"
PLACE_TOOL = "place_equity_order"


def _resolve_agentic_account(client: RobinhoodMcpClient, account_number: str | None) -> str:
    if account_number and account_number.strip():
        return account_number.strip()
    raw_accounts = client.call_tool("get_accounts", {})
    accounts = extract_accounts(raw_accounts)
    picks = pick_probe_accounts(accounts)
    agentic = picks.get("agentic")
    if not agentic or not agentic.get("account_number"):
        raise RuntimeError("No Agentic account found (agentic_allowed=true)")
    return str(agentic["account_number"])


def _order_args(body: dict[str, Any], account_number: str) -> dict[str, Any]:
    symbol = str(body.get("symbol", "")).strip().upper()
    side = str(body.get("side", "")).strip().lower()
    order_type = str(body.get("type", body.get("order_type", "market"))).strip().lower()
    if not symbol:
        raise ValueError("symbol is required")
    if side not in {"buy", "sell"}:
        raise ValueError("side must be buy or sell")
    if order_type not in {"market", "limit"}:
        raise ValueError("type must be market or limit")

    args: dict[str, Any] = {
        "account_number": account_number,
        "symbol": symbol,
        "side": side,
        "type": order_type,
    }
    tif = body.get("time_in_force")
    if tif:
        args["time_in_force"] = str(tif).strip().lower()
    qty = body.get("quantity")
    if qty is not None and str(qty).strip():
        args["quantity"] = qty
    amount = body.get("amount") or body.get("dollar_amount")
    if amount is not None and str(amount).strip():
        args["amount"] = amount
    limit_price = body.get("limit_price")
    if limit_price is not None and str(limit_price).strip():
        args["limit_price"] = limit_price
    if order_type == "limit" and "limit_price" not in args:
        raise ValueError("limit_price is required for limit orders")
    if "quantity" not in args and "amount" not in args:
        raise ValueError("quantity or amount is required")
    return args


def _estimate_notional(review_data: Any, order_args: dict[str, Any]) -> float | None:
    if isinstance(review_data, dict):
        for key in ("estimated_cost", "total_cost", "notional", "order_cost", "value"):
            val = review_data.get(key)
            if val is not None:
                try:
                    return float(val)
                except (TypeError, ValueError):
                    pass
        for nested in ("order", "preview", "data"):
            inner = review_data.get(nested)
            if isinstance(inner, dict):
                est = _estimate_notional(inner, order_args)
                if est is not None:
                    return est
    qty = order_args.get("quantity")
    price = order_args.get("limit_price")
    if qty is not None and price is not None:
        try:
            return float(qty) * float(price)
        except (TypeError, ValueError):
            pass
    amount = order_args.get("amount")
    if amount is not None:
        try:
            return float(amount)
        except (TypeError, ValueError):
            pass
    return None


def run_review(access_token: str, body: dict[str, Any]) -> dict[str, Any]:
    client = RobinhoodMcpClient(access_token=access_token)
    started = datetime.now(timezone.utc).isoformat()
    try:
        client.initialize()
        account_number = _resolve_agentic_account(client, body.get("account_number"))
        order_args = _order_args(body, account_number)
        raw = client.call_tool(REVIEW_TOOL, order_args)
        review_data = parse_tool_payload(raw)
        notional = _estimate_notional(review_data, order_args)
        return {
            "ok": True,
            "started_at": started,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "account_number": account_number,
            "order_args": order_args,
            "review": review_data,
            "estimated_notional": notional,
        }
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass


def run_place(access_token: str, body: dict[str, Any]) -> dict[str, Any]:
    client = RobinhoodMcpClient(access_token=access_token)
    started = datetime.now(timezone.utc).isoformat()
    try:
        client.initialize()
        account_number = _resolve_agentic_account(client, body.get("account_number"))
        order_args = _order_args(body, account_number)
        raw = client.call_tool(PLACE_TOOL, order_args)
        place_data = parse_tool_payload(raw)
        order_id = None
        if isinstance(place_data, dict):
            order_id = place_data.get("id") or place_data.get("order_id")
            if order_id is None and isinstance(place_data.get("order"), dict):
                order_id = place_data["order"].get("id") or place_data["order"].get("order_id")
        return {
            "ok": True,
            "started_at": started,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "account_number": account_number,
            "order_args": order_args,
            "result": place_data,
            "order_id": str(order_id) if order_id else None,
        }
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass
