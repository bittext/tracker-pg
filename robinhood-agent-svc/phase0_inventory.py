#!/usr/bin/env python3
"""
Phase 0 — authenticated MCP tool inventory for Robinhood Agentic Trading.

Requires .tokens.json from phase0_oauth.py. Initializes an MCP session, lists
tools, optionally calls read-only tools, and writes findings/tool-inventory.json.

Usage:
  python phase0_inventory.py
  python phase0_inventory.py --probe-read-tools
  python phase0_inventory.py --tool get_portfolio   # name varies; see inventory
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from mcp_client import RobinhoodMcpClient

TOKENS_PATH = Path(__file__).resolve().parent / ".tokens.json"
FINDINGS_DIR = Path(__file__).resolve().parent / "findings"
DEFAULT_OUTPUT = FINDINGS_DIR / "tool-inventory.json"

# Heuristic: tool names that look read-only (adjust after first inventory run).
READ_ONLY_HINTS = (
    "get",
    "list",
    "fetch",
    "read",
    "portfolio",
    "account",
    "position",
    "balance",
    "quote",
    "history",
    "order",
)


def load_access_token() -> str:
    if not TOKENS_PATH.exists():
        raise SystemExit(f"Missing {TOKENS_PATH} — run: python phase0_oauth.py")
    data = json.loads(TOKENS_PATH.read_text(encoding="utf-8"))
    token = data.get("access_token")
    if not token:
        raise SystemExit(f"No access_token in {TOKENS_PATH}")
    return str(token)


def looks_read_only(name: str) -> bool:
    lower = name.lower()
    return any(hint in lower for hint in READ_ONLY_HINTS)


def summarize_tool(tool: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": tool.get("name"),
        "description": tool.get("description"),
        "inputSchema": tool.get("inputSchema"),
        "annotations": tool.get("annotations"),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Robinhood MCP tool inventory")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--probe-read-tools",
        action="store_true",
        help="Call tools that look read-only (best-effort; may fail if args required)",
    )
    parser.add_argument("--tool", metavar="NAME", help="Call a single tool with empty args")
    args = parser.parse_args()

    access_token = load_access_token()
    client = RobinhoodMcpClient(access_token=access_token)

    print("Initializing MCP session…")
    init_result = client.initialize()
    print(f"  server: {init_result.get('serverInfo')}")
    print(f"  protocol: {init_result.get('protocolVersion')}")

    print("Listing tools…")
    tools = client.list_tools()
    print(f"  found {len(tools)} tool(s)")

    inventory: dict[str, Any] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "initialize_result": init_result,
        "tool_count": len(tools),
        "tools": [summarize_tool(t) for t in tools],
        "probe_results": [],
    }

    for tool in tools:
        name = tool.get("name", "?")
        desc = (tool.get("description") or "")[:80]
        print(f"  • {name}: {desc}")

    if args.tool:
        print(f"\nCalling tool {args.tool!r}…")
        try:
            result = client.call_tool(args.tool, {})
            inventory["probe_results"].append({"tool": args.tool, "ok": True, "result": result})
            print(json.dumps(result, indent=2)[:2000])
        except Exception as exc:  # noqa: BLE001 — phase0 spike
            inventory["probe_results"].append({"tool": args.tool, "ok": False, "error": str(exc)})
            print(f"  failed: {exc}")

    elif args.probe_read_tools:
        print("\nProbing read-like tools (empty args)…")
        for tool in tools:
            name = str(tool.get("name", ""))
            if not name or not looks_read_only(name):
                continue
            print(f"  trying {name}…", end=" ")
            try:
                result = client.call_tool(name, {})
                inventory["probe_results"].append({"tool": name, "ok": True, "result": result})
                print("ok")
            except Exception as exc:  # noqa: BLE001
                inventory["probe_results"].append({"tool": name, "ok": False, "error": str(exc)})
                print(f"failed ({exc})")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(inventory, indent=2) + "\n", encoding="utf-8")
    print(f"\nWrote {args.output}")

    try:
        client.close_session()
    except Exception:  # noqa: BLE001
        pass

    return 0


if __name__ == "__main__":
    sys.exit(main())
