"""Read-only Robinhood Banking MCP sync for tracker-pg Agentic Credit Card."""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

from mcp_client import RobinhoodMcpClient
from mcp_tool_utils import parse_tool_payload

LOGGER = logging.getLogger(__name__)

BANKING_MCP_ENDPOINT = "https://banking-agent.robinhood.com/mcp/banking"
MICRO = 1_000_000

STATUS_TOOL = "banking_get_agent_card_status"
BALANCE_TOOL = "banking_get_agent_card_balance"
POLICY_TOOL = "banking_get_agent_card_policy"
TRANSACTIONS_TOOL = "banking_get_agent_card_transactions"


def _micro_to_usd(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return round(float(value) / MICRO, 2)
    except (TypeError, ValueError):
        return None


def _parse_card_last4(status_payload: dict[str, Any]) -> str:
    for key in ("last4", "lastFour", "cardLast4"):
        raw = status_payload.get(key)
        if raw and str(raw).strip().isdigit() and len(str(raw).strip()) == 4:
            return str(raw).strip()
    masked = status_payload.get("maskedCardNumber") or status_payload.get("masked_card_number")
    if isinstance(masked, str):
        digits = "".join(ch for ch in masked if ch.isdigit())
        if len(digits) >= 4:
            return digits[-4:]
    return ""


def _normalize_transactions(raw: Any) -> list[dict[str, Any]]:
    if not isinstance(raw, dict):
        return []
    data = raw.get("data")
    if not isinstance(data, dict):
        return []
    search = data.get("transactionSearch")
    if not isinstance(search, dict):
        return []
    items = search.get("items")
    if not isinstance(items, list):
        return []
    rows: list[dict[str, Any]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        merchant = item.get("merchantDetails") if isinstance(item.get("merchantDetails"), dict) else {}
        merchant_name = str(merchant.get("merchantName") or merchant.get("name") or "").strip()
        amount_micro = item.get("amountMicro")
        txn_at = item.get("transactionAt")
        occurred_at = None
        if txn_at is not None:
            try:
                occurred_at = datetime.fromtimestamp(int(txn_at) / 1000, tz=timezone.utc).isoformat()
            except (TypeError, ValueError, OSError):
                occurred_at = None
        rows.append(
            {
                "external_id": str(item.get("id") or item.get("transactionId") or ""),
                "merchant_name": merchant_name,
                "description": str(item.get("description") or merchant_name or "").strip(),
                "amount_micro": amount_micro,
                "amount_usd": _micro_to_usd(amount_micro),
                "transaction_status": str(item.get("transactionStatus") or "").strip(),
                "transaction_at": occurred_at,
            }
        )
    return [r for r in rows if r.get("external_id")]


def run_banking_sync(access_token: str, *, transaction_limit: int = 20) -> dict[str, Any]:
    limit = max(1, min(int(transaction_limit), 50))
    client = RobinhoodMcpClient(access_token=access_token, endpoint=BANKING_MCP_ENDPOINT)
    try:
        client.initialize()
        tools = {t.get("name") for t in client.list_tools() if isinstance(t, dict)}
        for required in (STATUS_TOOL, BALANCE_TOOL, POLICY_TOOL, TRANSACTIONS_TOOL):
            if required not in tools:
                raise RuntimeError(f"Banking MCP missing tool {required!r}")

        status_raw = parse_tool_payload(client.call_tool(STATUS_TOOL, {}))
        balance_raw = parse_tool_payload(client.call_tool(BALANCE_TOOL, {}))
        policy_raw = parse_tool_payload(client.call_tool(POLICY_TOOL, {}))
        txn_raw = parse_tool_payload(client.call_tool(TRANSACTIONS_TOOL, {"limit": limit}))

        status_payload = status_raw if isinstance(status_raw, dict) else {}
        balance_payload = balance_raw if isinstance(balance_raw, dict) else {}
        policy_payload = policy_raw if isinstance(policy_raw, dict) else {}

        monthly_limit_micro = balance_payload.get("monthlyLimit")
        if monthly_limit_micro is None:
            monthly_limit_micro = policy_payload.get("monthlyLimit")
        total_spend_micro = balance_payload.get("totalSpendMicro")
        if total_spend_micro is None:
            total_spend_micro = policy_payload.get("totalSpendMicro")

        available_balance_micro = balance_payload.get("availableBalance")
        if available_balance_micro is None and monthly_limit_micro is not None and total_spend_micro is not None:
            try:
                available_balance_micro = int(monthly_limit_micro) - int(total_spend_micro)
            except (TypeError, ValueError):
                available_balance_micro = None

        card_status = str(status_payload.get("cardStatus") or "").strip()
        activation_status = str(status_payload.get("status") or "").strip()
        card_last4 = _parse_card_last4(status_payload)

        transactions = _normalize_transactions(txn_raw if isinstance(txn_raw, dict) else {})
        return {
            "ok": True,
            "synced_at": datetime.now(timezone.utc).isoformat(),
            "card_last_four": card_last4,
            "card_status": card_status,
            "activation_status": activation_status,
            "monthly_limit_micro": monthly_limit_micro,
            "monthly_limit_usd": _micro_to_usd(monthly_limit_micro),
            "total_spend_micro": total_spend_micro,
            "total_spend_usd": _micro_to_usd(total_spend_micro),
            "available_balance_micro": available_balance_micro,
            "available_balance_usd": _micro_to_usd(available_balance_micro),
            "transactions": transactions,
            "transaction_count": len(transactions),
        }
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            LOGGER.debug("banking MCP session close failed", exc_info=True)
