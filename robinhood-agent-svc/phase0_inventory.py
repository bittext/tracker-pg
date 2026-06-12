#!/usr/bin/env python3
"""
Phase 0 — authenticated MCP tool inventory for Robinhood Agentic Trading.

Requires .tokens.json from phase0_oauth.py. Initializes an MCP session, lists
tools, optionally calls read-only tools, and writes findings/tool-inventory.json.

Usage:
  python phase0_inventory.py
  python phase0_inventory.py --probe-read-tools
  python phase0_inventory.py --probe-chain
  python phase0_inventory.py --tool get_portfolio --args '{"account_number":"..."}'
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from mcp_client import RobinhoodMcpClient
from mcp_tool_utils import extract_accounts, parse_tool_payload, pick_probe_accounts

TOKENS_PATH = Path(__file__).resolve().parent / ".tokens.json"
FINDINGS_DIR = Path(__file__).resolve().parent / "findings"
DEFAULT_OUTPUT = FINDINGS_DIR / "tool-inventory.json"

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

CHAIN_READ_TOOLS: tuple[tuple[str, dict[str, Any]], ...] = (
    ("get_portfolio", {}),
    ("get_equity_positions", {}),
    ("get_option_positions", {}),
    ("get_equity_orders", {"limit": 5}),
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


def account_label(account: dict[str, Any]) -> str:
    nick = account.get("nickname") or account.get("brokerage_account_type") or "account"
    num = str(account.get("account_number", ""))
    masked = f"•••{num[-4:]}" if len(num) >= 4 else num
    flags = []
    if account.get("is_default"):
        flags.append("default")
    if account.get("agentic_allowed"):
        flags.append("agentic")
    suffix = f" ({', '.join(flags)})" if flags else ""
    return f"{nick} {masked}{suffix}"


def probe_result_entry(tool: str, ok: bool, *, result: Any = None, error: str | None = None) -> dict[str, Any]:
    entry: dict[str, Any] = {"tool": tool, "ok": ok}
    if ok:
        entry["result"] = result
    else:
        entry["error"] = error
    return entry


def run_probe_chain(client: RobinhoodMcpClient) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []

    print("\nChained read probe (get_accounts → portfolio / positions / orders)…")
    try:
        raw_accounts = client.call_tool("get_accounts", {})
        accounts = extract_accounts(raw_accounts)
        results.append(
            probe_result_entry(
                "get_accounts",
                True,
                result={"account_count": len(accounts), "accounts_summary": [
                    {
                        "account_number_masked": f"•••{str(a.get('account_number', ''))[-4:]}",
                        "brokerage_account_type": a.get("brokerage_account_type"),
                        "nickname": a.get("nickname"),
                        "is_default": a.get("is_default"),
                        "agentic_allowed": a.get("agentic_allowed"),
                        "management_type": a.get("management_type"),
                    }
                    for a in accounts
                ]},
            )
        )
        print(f"  get_accounts: {len(accounts)} account(s)")
        for a in accounts:
            print(f"    • {account_label(a)}")
    except Exception as exc:  # noqa: BLE001
        results.append(probe_result_entry("get_accounts", False, error=str(exc)))
        print(f"  get_accounts failed: {exc}")
        return results

    picks = pick_probe_accounts(accounts)
    for role, account in picks.items():
        if not account:
            print(f"  skip {role}: no matching account")
            continue
        acct_num = str(account["account_number"])
        print(f"\n  Probing {role} account: {account_label(account)}")
        for tool_name, extra_args in CHAIN_READ_TOOLS:
            args = {"account_number": acct_num, **extra_args}
            print(f"    {tool_name}…", end=" ")
            try:
                raw = client.call_tool(tool_name, args)
                parsed = parse_tool_payload(raw)
                results.append(
                    probe_result_entry(
                        f"{tool_name}:{role}",
                        True,
                        result={"account_number_masked": f"•••{acct_num[-4:]}", "data": parsed},
                    )
                )
                print("ok")
            except Exception as exc:  # noqa: BLE001
                results.append(probe_result_entry(f"{tool_name}:{role}", False, error=str(exc)))
                print(f"failed ({exc})")

    return results


def main() -> int:
    parser = argparse.ArgumentParser(description="Robinhood MCP tool inventory")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--probe-read-tools",
        action="store_true",
        help="Call tools that look read-only with empty args (many will fail)",
    )
    parser.add_argument(
        "--probe-chain",
        action="store_true",
        help="get_accounts then portfolio/positions/orders for agentic + default accounts",
    )
    parser.add_argument("--tool", metavar="NAME", help="Call a single tool")
    parser.add_argument(
        "--args",
        default="{}",
        help='JSON object of tool arguments (with --tool), e.g. \'{"account_number":"123"}\'',
    )
    args = parser.parse_args()

    tool_args: dict[str, Any] = json.loads(args.args)

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
            result = client.call_tool(args.tool, tool_args)
            inventory["probe_results"].append(probe_result_entry(args.tool, True, result=result))
            print(json.dumps(parse_tool_payload(result), indent=2)[:4000])
        except Exception as exc:  # noqa: BLE001
            inventory["probe_results"].append(probe_result_entry(args.tool, False, error=str(exc)))
            print(f"  failed: {exc}")

    elif args.probe_chain:
        inventory["probe_results"] = run_probe_chain(client)

    elif args.probe_read_tools:
        print("\nProbing read-like tools (empty args)…")
        for tool in tools:
            name = str(tool.get("name", ""))
            if not name or not looks_read_only(name):
                continue
            print(f"  trying {name}…", end=" ")
            try:
                result = client.call_tool(name, {})
                inventory["probe_results"].append(probe_result_entry(name, True, result=result))
                print("ok")
            except Exception as exc:  # noqa: BLE001
                inventory["probe_results"].append(probe_result_entry(name, False, error=str(exc)))
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
