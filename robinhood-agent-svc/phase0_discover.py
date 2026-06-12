#!/usr/bin/env python3
"""
Phase 0 — unauthenticated discovery for Robinhood Agentic Trading MCP.

Probes OAuth metadata, dynamic client registration, and MCP endpoint behavior
without credentials. Safe to run anytime.

Usage:
  python phase0_discover.py
  python phase0_discover.py --write findings/discovery.json
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx

MCP_ENDPOINT = "https://agent.robinhood.com/mcp/trading"
OAUTH_AS_METADATA = "https://agent.robinhood.com/.well-known/oauth-authorization-server"
OAUTH_PR_METADATA = "https://agent.robinhood.com/.well-known/oauth-protected-resource/mcp/trading"
REGISTER_ENDPOINT = "https://agent.robinhood.com/oauth/trading/register"
DEFAULT_REDIRECT = "http://127.0.0.1:8765/callback"


def fetch_json(url: str, *, method: str = "GET", payload: dict[str, Any] | None = None) -> tuple[int, Any, dict[str, str]]:
    with httpx.Client(timeout=30.0, follow_redirects=True) as client:
        if method == "POST":
            response = client.post(url, json=payload, headers={"Content-Type": "application/json"})
        else:
            response = client.get(url)
    headers = {k.lower(): v for k, v in response.headers.items()}
    body: Any
    if response.headers.get("content-type", "").startswith("application/json"):
        body = response.json()
    else:
        body = response.text
    return response.status_code, body, headers


def probe_mcp_initialize() -> dict[str, Any]:
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "tracker-pg-discover", "version": "0.1.0"},
        },
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(
            MCP_ENDPOINT,
            json=payload,
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json, text/event-stream",
            },
        )
    return {
        "status_code": response.status_code,
        "body": response.text[:500],
        "headers": {
            "www-authenticate": response.headers.get("www-authenticate"),
            "access-control-allow-headers": response.headers.get("access-control-allow-headers"),
            "access-control-allow-methods": response.headers.get("access-control-allow-methods"),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Robinhood MCP Phase 0 discovery (no auth)")
    parser.add_argument("--write", metavar="PATH", type=Path, help="Write JSON findings to this path")
    args = parser.parse_args()

    findings: dict[str, Any] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "mcp_endpoint": MCP_ENDPOINT,
        "oauth_authorization_server_metadata": None,
        "oauth_protected_resource_metadata": None,
        "dynamic_client_registration": None,
        "unauthenticated_initialize_probe": None,
    }

    print("Robinhood Agentic Trading — Phase 0 discovery\n")

    print("1. OAuth authorization server metadata")
    status, body, _ = fetch_json(OAUTH_AS_METADATA)
    findings["oauth_authorization_server_metadata"] = {"url": OAUTH_AS_METADATA, "status": status, "body": body}
    print(f"   GET {OAUTH_AS_METADATA}")
    print(f"   status={status}")
    if isinstance(body, dict):
        for key in (
            "issuer",
            "authorization_endpoint",
            "token_endpoint",
            "registration_endpoint",
            "scopes_supported",
            "grant_types_supported",
            "code_challenge_methods_supported",
        ):
            if key in body:
                print(f"   {key}: {body[key]}")
    print()

    print("2. OAuth protected resource metadata")
    status, body, _ = fetch_json(OAUTH_PR_METADATA)
    findings["oauth_protected_resource_metadata"] = {"url": OAUTH_PR_METADATA, "status": status, "body": body}
    print(f"   GET {OAUTH_PR_METADATA}")
    print(f"   status={status} body={json.dumps(body, indent=2) if isinstance(body, dict) else body}")
    print()

    print("3. Dynamic client registration (ephemeral test client)")
    reg_payload = {
        "client_name": "tracker-pg-phase0-discover",
        "redirect_uris": [DEFAULT_REDIRECT],
        "grant_types": ["authorization_code", "refresh_token"],
        "response_types": ["code"],
        "token_endpoint_auth_method": "none",
    }
    status, body, _ = fetch_json(REGISTER_ENDPOINT, method="POST", payload=reg_payload)
    findings["dynamic_client_registration"] = {
        "url": REGISTER_ENDPOINT,
        "status": status,
        "request": reg_payload,
        "body": body,
    }
    print(f"   POST {REGISTER_ENDPOINT}")
    print(f"   status={status}")
    if isinstance(body, dict) and "client_id" in body:
        print(f"   client_id={body['client_id'][:8]}… (truncated)")
        print("   registration works — use phase0_oauth.py for interactive auth")
    else:
        print(f"   body={body}")
    print()

    print("4. Unauthenticated MCP initialize probe (expect 401)")
    probe = probe_mcp_initialize()
    findings["unauthenticated_initialize_probe"] = probe
    print(f"   POST {MCP_ENDPOINT}")
    print(f"   status={probe['status_code']} body={probe['body']!r}")
    if probe["headers"].get("www-authenticate"):
        print(f"   www-authenticate: {probe['headers']['www-authenticate']}")
    print()

    print("Next steps:")
    print("  1. Open Agentic account on desktop (Robinhood app / web after MCP connect)")
    print("  2. python phase0_oauth.py          # OAuth PKCE → saves .tokens.json")
    print("  3. python phase0_inventory.py      # list MCP tools → findings/tool-inventory.json")
    print("  4. Cursor: Settings → Tools & MCPs → https://agent.robinhood.com/mcp/trading")

    if args.write:
        args.write.parent.mkdir(parents=True, exist_ok=True)
        with open(args.write, "w", encoding="utf-8") as fh:
            json.dump(findings, fh, indent=2)
            fh.write("\n")
        print(f"\nWrote {args.write}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
