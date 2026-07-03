"""Parse Robinhood MCP tools/call payloads (content[].text JSON + structuredContent)."""

from __future__ import annotations

import json
from typing import Any


def parse_json_leading_object(text: str) -> Any | None:
    """Parse JSON when Robinhood appends interpretive prose after the object."""
    stripped = text.strip()
    if not stripped:
        return None
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        pass
    start = stripped.find("{")
    if start < 0:
        return None
    depth = 0
    for index in range(start, len(stripped)):
        char = stripped[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                try:
                    return json.loads(stripped[start : index + 1])
                except json.JSONDecodeError:
                    return None
    return None


def parse_tool_payload(result: dict[str, Any]) -> Any:
    """Return parsed business data from a tools/call result."""
    structured = result.get("structuredContent")
    if structured is not None:
        return structured

    for block in result.get("content") or []:
        if not isinstance(block, dict):
            continue
        if block.get("type") != "text":
            continue
        text = (block.get("text") or "").strip()
        if not text:
            continue
        parsed = parse_json_leading_object(text)
        if parsed is not None:
            return parsed
        return text
    return result


def extract_accounts(result: dict[str, Any]) -> list[dict[str, Any]]:
    """Pull account dicts from get_accounts tools/call result."""
    payload = parse_tool_payload(result)
    if not isinstance(payload, dict):
        return []

    accounts = payload.get("data", {}).get("accounts")
    if isinstance(accounts, list):
        return [a for a in accounts if isinstance(a, dict)]

    # structuredContent may nest under data directly
    if isinstance(payload.get("accounts"), list):
        return [a for a in payload["accounts"] if isinstance(a, dict)]
    return []


def pick_probe_accounts(accounts: list[dict[str, Any]]) -> dict[str, dict[str, Any] | None]:
    """Choose agentic + default accounts for chained read probes."""
    agentic = next((a for a in accounts if a.get("agentic_allowed")), None)
    default = next((a for a in accounts if a.get("is_default")), None)
    return {"agentic": agentic, "default": default}


def infer_account_role(account: dict[str, Any]) -> str:
    if account.get("agentic_allowed"):
        return "agentic"
    if account.get("is_default"):
        return "default"
    nick = str(account.get("nickname") or "").lower()
    btype = str(account.get("brokerage_account_type") or "").lower()
    if "managed" in nick or "managed" in btype:
        return "managed"
    return "other"


def _role_order(role: str) -> int:
    return {"agentic": 0, "default": 1, "managed": 2}.get(role, 3)


def build_sync_targets(accounts: list[dict[str, Any]]) -> list[tuple[str, dict[str, Any]]]:
    """All unique Robinhood accounts, labeled for sync (agentic, default, managed, other)."""
    seen: set[str] = set()
    targets: list[tuple[str, dict[str, Any]]] = []
    for account in accounts:
        acct_num = str(account.get("account_number") or "").strip()
        if not acct_num or acct_num in seen:
            continue
        seen.add(acct_num)
        targets.append((infer_account_role(account), account))
    targets.sort(key=lambda t: (_role_order(t[0]), str(t[1].get("account_number", ""))))
    return targets


def list_tool_names(tools: list[dict[str, Any]]) -> set[str]:
    return {str(t.get("name", "")).strip() for t in tools if t.get("name")}
