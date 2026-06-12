#!/usr/bin/env python3
"""
Phase 0 — OAuth PKCE flow for Robinhood Agentic Trading MCP.

Opens a browser for Robinhood login/consent, captures the authorization code
on a local callback, exchanges it for tokens, and saves them to .tokens.json.

Usage:
  python phase0_oauth.py
  python phase0_oauth.py --refresh   # use refresh_token in .tokens.json
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import secrets
import sys
import threading
import urllib.parse
import webbrowser
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any

import httpx

OAUTH_AS_METADATA = "https://agent.robinhood.com/.well-known/oauth-authorization-server"
REGISTER_ENDPOINT = "https://agent.robinhood.com/oauth/trading/register"
DEFAULT_REDIRECT = "http://127.0.0.1:8765/callback"
DEFAULT_SCOPE = "internal"
TOKENS_PATH = Path(__file__).resolve().parent / ".tokens.json"
CLIENT_PATH = Path(__file__).resolve().parent / ".oauth-client.json"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def pkce_pair() -> tuple[str, str]:
    verifier = b64url(secrets.token_bytes(32))
    challenge = b64url(hashlib.sha256(verifier.encode("ascii")).digest())
    return verifier, challenge


def load_oauth_metadata() -> dict[str, Any]:
    with httpx.Client(timeout=30.0) as client:
        response = client.get(OAUTH_AS_METADATA)
        response.raise_for_status()
        body = response.json()
    if not isinstance(body, dict):
        raise RuntimeError("unexpected oauth metadata")
    return body


def register_client(redirect_uri: str) -> dict[str, Any]:
    if CLIENT_PATH.exists():
        saved = json.loads(CLIENT_PATH.read_text(encoding="utf-8"))
        if saved.get("redirect_uri") == redirect_uri and saved.get("client_id"):
            return saved

    payload = {
        "client_name": "tracker-pg-phase0",
        "redirect_uris": [redirect_uri],
        "grant_types": ["authorization_code", "refresh_token"],
        "response_types": ["code"],
        "token_endpoint_auth_method": "none",
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(REGISTER_ENDPOINT, json=payload)
        response.raise_for_status()
        body = response.json()
    if not isinstance(body, dict) or "client_id" not in body:
        raise RuntimeError(f"registration failed: {body!r}")

    record = {
        "client_id": body["client_id"],
        "redirect_uri": redirect_uri,
        "registered_at": datetime.now(timezone.utc).isoformat(),
    }
    CLIENT_PATH.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    return record


def exchange_code(
    token_endpoint: str,
    client_id: str,
    redirect_uri: str,
    code: str,
    code_verifier: str,
) -> dict[str, Any]:
    data = {
        "grant_type": "authorization_code",
        "client_id": client_id,
        "code": code,
        "redirect_uri": redirect_uri,
        "code_verifier": code_verifier,
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(token_endpoint, data=data)
        if response.status_code >= 400:
            raise RuntimeError(f"token exchange failed ({response.status_code}): {response.text}")
        body = response.json()
    if not isinstance(body, dict):
        raise RuntimeError("token response not an object")
    return body


def refresh_tokens(token_endpoint: str, client_id: str, refresh_token: str) -> dict[str, Any]:
    data = {
        "grant_type": "refresh_token",
        "client_id": client_id,
        "refresh_token": refresh_token,
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(token_endpoint, data=data)
        if response.status_code >= 400:
            raise RuntimeError(f"refresh failed ({response.status_code}): {response.text}")
        body = response.json()
    if not isinstance(body, dict):
        raise RuntimeError("refresh response not an object")
    return body


def save_tokens(tokens: dict[str, Any], *, source: str) -> None:
    record = {
        "saved_at": datetime.now(timezone.utc).isoformat(),
        "source": source,
        **tokens,
    }
    TOKENS_PATH.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(f"Saved tokens to {TOKENS_PATH}")


def interactive_authorize(
    authorization_endpoint: str,
    token_endpoint: str,
    client_id: str,
    redirect_uri: str,
    scope: str,
) -> dict[str, Any]:
    code_verifier, code_challenge = pkce_pair()
    state = secrets.token_urlsafe(16)

    params = {
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "scope": scope,
        "state": state,
        "code_challenge": code_challenge,
        "code_challenge_method": "S256",
    }
    auth_url = authorization_endpoint + "?" + urllib.parse.urlencode(params)

    result: dict[str, str | None] = {"code": None, "error": None}
    parsed_redirect = urllib.parse.urlparse(redirect_uri)
    host = parsed_redirect.hostname or "127.0.0.1"
    port = parsed_redirect.port or 8765

    class CallbackHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if query.get("state", [None])[0] != state:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b"state mismatch")
                result["error"] = "state mismatch"
                return
            if "error" in query:
                result["error"] = query["error"][0]
                self.send_response(400)
                self.end_headers()
                self.wfile.write(f"OAuth error: {result['error']}".encode())
                return
            code = query.get("code", [None])[0]
            result["code"] = code
            self.send_response(200)
            self.end_headers()
            self.wfile.write(
                b"<html><body><h1>Robinhood OAuth complete</h1>"
                b"<p>You can close this tab and return to the terminal.</p></body></html>"
            )

        def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
            return

    server = HTTPServer((host, port), CallbackHandler)
    thread = threading.Thread(target=server.handle_request, daemon=True)
    thread.start()

    print("Opening browser for Robinhood OAuth…")
    print(f"If it does not open, visit:\n{auth_url}\n")
    webbrowser.open(auth_url)
    thread.join(timeout=300)
    server.server_close()

    if result["error"]:
        raise RuntimeError(f"OAuth error: {result['error']}")
    if not result["code"]:
        raise RuntimeError("Timed out waiting for OAuth callback (5 min)")

    tokens = exchange_code(
        token_endpoint,
        client_id,
        redirect_uri,
        str(result["code"]),
        code_verifier,
    )
    save_tokens(tokens, source="authorization_code")
    return tokens


def main() -> int:
    parser = argparse.ArgumentParser(description="Robinhood MCP OAuth (PKCE)")
    parser.add_argument("--redirect-uri", default=DEFAULT_REDIRECT)
    parser.add_argument("--scope", default=DEFAULT_SCOPE)
    parser.add_argument("--refresh", action="store_true", help="Refresh using .tokens.json")
    args = parser.parse_args()

    metadata = load_oauth_metadata()
    authorization_endpoint = str(metadata["authorization_endpoint"])
    token_endpoint = str(metadata["token_endpoint"])
    client = register_client(args.redirect_uri)

    if args.refresh:
        if not TOKENS_PATH.exists():
            print(f"No token file at {TOKENS_PATH}", file=sys.stderr)
            return 1
        saved = json.loads(TOKENS_PATH.read_text(encoding="utf-8"))
        refresh_token = saved.get("refresh_token")
        if not refresh_token:
            print("No refresh_token in .tokens.json — run without --refresh", file=sys.stderr)
            return 1
        tokens = refresh_tokens(token_endpoint, client["client_id"], refresh_token)
        save_tokens(tokens, source="refresh_token")
        return 0

    print("Requirements before auth:")
    print("  • Primary Robinhood account in good standing")
    print("  • Desktop browser (Agentic onboarding is desktop-only)")
    print("  • Fund Agentic account when prompted during onboarding")
    print()

    tokens = interactive_authorize(
        authorization_endpoint,
        token_endpoint,
        client["client_id"],
        args.redirect_uri,
        args.scope,
    )
    print(f"access_token={tokens.get('access_token', '')[:12]}…")
    if tokens.get("refresh_token"):
        print("refresh_token received")
    print("\nRun: python phase0_inventory.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
