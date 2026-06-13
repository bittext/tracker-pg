"""Parse Robinhood MCP tools/call payloads (content[].text JSON + structuredContent)."""

from __future__ import annotations

import json
from typing import Any


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
        try:
            return json.loads(text)
        except json.JSONDecodeError:
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


def list_tool_names(tools: list[dict[str, Any]]) -> set[str]:
    return {str(t.get("name", "")).strip() for t in tools if t.get("name")}
