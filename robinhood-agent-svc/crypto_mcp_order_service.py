"""Crypto order review and placement via Robinhood MCP on the Agentic account."""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from typing import Any

from mcp_client import RobinhoodMcpClient
from mcp_tool_utils import extract_accounts, parse_tool_payload, pick_probe_accounts

PREVIEW_TOOL = "preview_crypto_order"
PLACE_TOOL = "place_crypto_order"
POSITIONS_TOOL = "get_crypto_positions"


def _asset_code(symbol: str) -> str:
    raw = symbol.strip().upper()
    if raw.endswith("-USD"):
        return raw[:-4]
    if raw.endswith("USD") and len(raw) > 3:
        return raw[:-3]
    return raw


def _resolve_agentic_crypto_account(client: RobinhoodMcpClient, account_number: str | None) -> str:
    raw_accounts = client.call_tool("get_accounts", {})
    accounts = extract_accounts(raw_accounts)
    picks = pick_probe_accounts(accounts)
    agentic = picks.get("agentic")
    if not agentic:
        raise RuntimeError("No Agentic account found for this connection")
    if account_number and str(account_number).strip() not in {
        str(agentic.get("account_number") or "").strip(),
        str(agentic.get("rhs_account_number") or "").strip(),
    }:
        raise RuntimeError("Crypto MCP orders can only target the Agentic account")
    rhs = str(agentic.get("rhs_account_number") or agentic.get("account_number") or "").strip()
    if not rhs:
        raise RuntimeError("Agentic account is missing rhs_account_number")
    rhc = str(agentic.get("rhc_account_number") or "").strip()
    if not rhc:
        raise RuntimeError(
            "Agentic account has no crypto account. Open crypto in the Robinhood app on Agentic first."
        )
    return rhs


def _positions_from_payload(payload: Any) -> list[dict[str, Any]]:
    data = payload
    if isinstance(payload, dict) and isinstance(payload.get("data"), dict):
        data = payload["data"]
    if not isinstance(data, dict):
        return []
    rows = data.get("results") or data.get("positions") or []
    return [row for row in rows if isinstance(row, dict)]


def _transferable_quantity(client: RobinhoodMcpClient, rhs_account_number: str, symbol: str) -> str:
    raw = client.call_tool(POSITIONS_TOOL, {"rhs_account_number": rhs_account_number})
    payload = parse_tool_payload(raw)
    want = _asset_code(symbol)
    for row in _positions_from_payload(payload):
        currency = row.get("currency") if isinstance(row.get("currency"), dict) else {}
        code = str(currency.get("code") or row.get("asset") or row.get("symbol") or "").upper()
        if code.endswith("-USD"):
            code = code[:-4]
        if code != want:
            continue
        qty = row.get("quantity_transferable")
        if qty is None:
            qty = row.get("quantity")
        if qty is None or str(qty).strip() == "":
            continue
        try:
            if Decimal(str(qty)) <= 0:
                continue
        except InvalidOperation as exc:
            raise ValueError(f"Invalid transferable quantity for {want}: {qty}") from exc
        return str(qty).strip()
    raise ValueError(f"No transferable {want} on the Agentic crypto account to sell")


def _order_args(body: dict[str, Any], rhs_account_number: str, client: RobinhoodMcpClient) -> dict[str, Any]:
    symbol = str(body.get("symbol", "")).strip().upper()
    side = str(body.get("side", "")).strip().lower()
    order_type = str(body.get("type", body.get("order_type", "market"))).strip().lower()
    sell_all = bool(body.get("sell_all") or body.get("sellAll"))
    if not symbol:
        raise ValueError("symbol is required")
    if side not in {"buy", "sell"}:
        raise ValueError("side must be buy or sell")
    if order_type not in {"market", "limit"}:
        raise ValueError("type must be market or limit")
    if sell_all and side != "sell":
        raise ValueError("sell_all is only valid for sell orders")

    args: dict[str, Any] = {
        "rhs_account_number": rhs_account_number,
        "symbol": symbol,
        "side": side,
        "type": order_type,
    }
    tif = body.get("time_in_force") or body.get("timeInForce")
    if tif:
        args["time_in_force"] = str(tif).strip().lower()
    limit_price = body.get("limit_price") or body.get("limitPrice")
    if limit_price is not None and str(limit_price).strip():
        args["limit_price"] = str(limit_price).strip()
    if order_type == "limit" and "limit_price" not in args:
        raise ValueError("limit_price is required for limit orders")

    if sell_all:
        args["quantity"] = _transferable_quantity(client, rhs_account_number, symbol)
        return args

    qty = body.get("quantity")
    if qty is not None and str(qty).strip():
        args["quantity"] = str(qty).strip()
    amount = body.get("dollar_amount") or body.get("amount")
    if amount is not None and str(amount).strip():
        args["dollar_amount"] = str(amount).strip()
    if "quantity" not in args and "dollar_amount" not in args:
        raise ValueError("quantity, dollar_amount, or sell_all is required")
    if "quantity" in args and "dollar_amount" in args:
        raise ValueError("provide exactly one of quantity or dollar_amount")
    return args


def _estimate_notional(review_data: Any, order_args: dict[str, Any]) -> float | None:
    if isinstance(review_data, dict):
        for key in (
            "estimated_cost",
            "estimated_credit",
            "total_cost",
            "notional",
            "order_cost",
            "value",
            "dollar_amount",
        ):
            val = review_data.get(key)
            if val is not None:
                try:
                    return abs(float(val))
                except (TypeError, ValueError):
                    pass
        for nested in ("order", "preview", "data"):
            inner = review_data.get(nested)
            if isinstance(inner, dict):
                est = _estimate_notional(inner, order_args)
                if est is not None:
                    return est
    amount = order_args.get("dollar_amount")
    if amount is not None:
        try:
            return abs(float(amount))
        except (TypeError, ValueError):
            pass
    qty = order_args.get("quantity")
    price = order_args.get("limit_price")
    if qty is not None and price is not None:
        try:
            return abs(float(qty) * float(price))
        except (TypeError, ValueError):
            pass
    return None


def _run(access_token: str, body: dict[str, Any], tool: str) -> dict[str, Any]:
    client = RobinhoodMcpClient(access_token=access_token)
    started = datetime.now(timezone.utc).isoformat()
    try:
        client.initialize()
        rhs = _resolve_agentic_crypto_account(client, body.get("account_number"))
        order_args = _order_args(body, rhs, client)
        if tool == PLACE_TOOL:
            order_args["ref_id"] = str(body.get("ref_id") or uuid.uuid4())
        raw = client.call_tool(tool, order_args)
        payload = parse_tool_payload(raw)
        notional = _estimate_notional(payload, order_args)
        order_id = None
        if isinstance(payload, dict):
            order_id = payload.get("id") or payload.get("order_id")
            nested = payload.get("order")
            if order_id is None and isinstance(nested, dict):
                order_id = nested.get("id") or nested.get("order_id")
        result: dict[str, Any] = {
            "ok": True,
            "started_at": started,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "account_number": rhs,
            "asset_class": "crypto",
            "order_args": {k: v for k, v in order_args.items() if k != "ref_id"},
            "estimated_notional": notional,
        }
        if tool == PREVIEW_TOOL:
            result["review"] = payload
        else:
            result["result"] = payload
            result["order_id"] = str(order_id) if order_id else None
        return result
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass


def run_crypto_mcp_review(access_token: str, body: dict[str, Any]) -> dict[str, Any]:
    return _run(access_token, body, PREVIEW_TOOL)


def run_crypto_mcp_place(access_token: str, body: dict[str, Any]) -> dict[str, Any]:
    return _run(access_token, body, PLACE_TOOL)
