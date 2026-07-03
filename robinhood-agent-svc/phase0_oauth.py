#!/usr/bin/env python3
"""
Phase 0 — OAuth PKCE flow for Robinhood Agentic Trading MCP.

Opens a browser for Robinhood login/consent, captures the authorization code
on a local callback, exchanges it for tokens, and saves them to .tokens.json.

Usage:
  python phase0_oauth.py
  python phase0_oauth.py --manual          # paste callback URL (SSH / AWS console)
  python phase0_oauth.py --refresh         # use refresh_token in .tokens.json
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
REGISTER_ENDPOINT_TRADING = "https://agent.robinhood.com/oauth/trading/register"
REGISTER_ENDPOINT_BANKING = "https://banking-agent.robinhood.com/oauth/banking/register"
DEFAULT_REDIRECT = "http://127.0.0.1:8765/callback"
DEFAULT_SCOPE_TRADING = "internal"
DEFAULT_SCOPE_BANKING = "credit-card"
TOKENS_PATH_TRADING = Path(__file__).resolve().parent / ".tokens.json"
TOKENS_PATH_BANKING = Path(__file__).resolve().parent / ".tokens-banking.json"
CLIENT_PATH_TRADING = Path(__file__).resolve().parent / ".oauth-client.json"
CLIENT_PATH_BANKING = Path(__file__).resolve().parent / ".oauth-banking-client.json"
PENDING_PATH_TRADING = Path(__file__).resolve().parent / ".oauth-pending.json"
PENDING_PATH_BANKING = Path(__file__).resolve().parent / ".oauth-banking-pending.json"


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


def register_client(redirect_uri: str, *, banking: bool = False) -> dict[str, Any]:
    client_path = CLIENT_PATH_BANKING if banking else CLIENT_PATH_TRADING
    register_endpoint = REGISTER_ENDPOINT_BANKING if banking else REGISTER_ENDPOINT_TRADING
    if client_path.exists():
        saved = json.loads(client_path.read_text(encoding="utf-8"))
        if saved.get("redirect_uri") == redirect_uri and saved.get("client_id"):
            return saved

    payload = {
        "client_name": "tracker-pg-phase0-banking" if banking else "tracker-pg-phase0",
        "redirect_uris": [redirect_uri],
        "grant_types": ["authorization_code", "refresh_token"],
        "response_types": ["code"],
        "token_endpoint_auth_method": "none",
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(register_endpoint, json=payload)
        response.raise_for_status()
        body = response.json()
    if not isinstance(body, dict) or "client_id" not in body:
        raise RuntimeError(f"registration failed: {body!r}")

    record = {
        "client_id": body["client_id"],
        "redirect_uri": redirect_uri,
        "registered_at": datetime.now(timezone.utc).isoformat(),
    }
    client_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
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


def save_tokens(tokens: dict[str, Any], *, source: str, banking: bool = False) -> None:
    tokens_path = TOKENS_PATH_BANKING if banking else TOKENS_PATH_TRADING
    record = {
        "saved_at": datetime.now(timezone.utc).isoformat(),
        "source": source,
        **tokens,
    }
    tokens_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(f"Saved tokens to {tokens_path}")


def parse_callback_input(raw: str) -> tuple[str, str | None]:
    """Extract authorization code and state from a pasted callback URL or bare code."""
    text = raw.strip()
    if not text:
        raise ValueError("empty input")
    if "://" in text or text.startswith("/"):
        parsed = urllib.parse.urlparse(text if "://" in text else f"http://127.0.0.1{text}")
        query = urllib.parse.parse_qs(parsed.query)
        code = query.get("code", [None])[0]
        state = query.get("state", [None])[0]
        if not code:
            raise ValueError("no code= in callback URL")
        return str(code), str(state) if state else None
    return text, None


def manual_authorize(
    authorization_endpoint: str,
    token_endpoint: str,
    client_id: str,
    redirect_uri: str,
    scope: str,
    *,
    callback_url: str | None = None,
    banking: bool = False,
) -> dict[str, Any]:
    """
    OAuth without a local callback server — for SSH/AWS console users.

    Open the printed URL in a browser on your laptop (not the AWS console browser).
    After Robinhood redirects, copy the full address bar URL (page may not load).
    """
    pending_path = PENDING_PATH_BANKING if banking else PENDING_PATH_TRADING
    if callback_url is not None and not pending_path.exists():
        raise RuntimeError(
            "No pending OAuth session — run `python phase0_oauth.py --manual` first, "
            "then paste the callback URL when prompted"
        )

    if pending_path.exists():
        pending = json.loads(pending_path.read_text(encoding="utf-8"))
        code_verifier = pending["code_verifier"]
        state = pending["state"]
        auth_url = pending["auth_url"]
        print("Resuming pending OAuth session…")
        print(f"If you need the link again:\n{auth_url}\n")
    else:
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
        pending_path.write_text(
            json.dumps(
                {
                    "code_verifier": code_verifier,
                    "state": state,
                    "client_id": client_id,
                    "redirect_uri": redirect_uri,
                    "auth_url": auth_url,
                    "created_at": datetime.now(timezone.utc).isoformat(),
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        print("Manual OAuth — use a browser ON YOUR LAPTOP, not the AWS console browser.\n")
        print("1. Copy this URL and open it in Chrome/Safari on your Mac:")
        print(f"\n{auth_url}\n")
        print("2. Log in to Robinhood and complete Agentic onboarding (desktop).")
        if banking:
            print("   (Banking MCP: complete Agentic Credit Card setup in Robinhood.)")
        print("3. You will be redirected to http://127.0.0.1:8765/callback?code=…")
        print("   The page may show “can't connect” — that is OK.")
        print("4. Copy the FULL URL from the address bar and paste it below.\n")

    if callback_url is None:
        callback_url = input("Paste callback URL: ").strip()

    code, returned_state = parse_callback_input(callback_url)
    if returned_state is not None and returned_state != state:
        raise RuntimeError("state mismatch — restart with: python phase0_oauth.py --manual")

    tokens = exchange_code(token_endpoint, client_id, redirect_uri, code, code_verifier)
    save_tokens(tokens, source="authorization_code", banking=banking)
    pending_path.unlink(missing_ok=True)
    return tokens


def interactive_authorize(
    authorization_endpoint: str,
    token_endpoint: str,
    client_id: str,
    redirect_uri: str,
    scope: str,
    *,
    banking: bool = False,
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
    save_tokens(tokens, source="authorization_code", banking=banking)
    return tokens


def main() -> int:
    parser = argparse.ArgumentParser(description="Robinhood MCP OAuth (PKCE)")
    parser.add_argument("--redirect-uri", default=DEFAULT_REDIRECT)
    parser.add_argument("--scope", default=None)
    parser.add_argument(
        "--banking",
        action="store_true",
        help="Banking MCP (credit-card scope) → .tokens-banking.json",
    )
    parser.add_argument("--refresh", action="store_true", help="Refresh using .tokens.json")
    parser.add_argument(
        "--manual",
        action="store_true",
        help="No local callback server — open URL on your laptop and paste the redirect URL",
    )
    parser.add_argument(
        "--callback-url",
        metavar="URL",
        help="With --manual: callback URL from browser address bar (skip prompt)",
    )
    args = parser.parse_args()
    banking = args.banking
    scope = args.scope or (DEFAULT_SCOPE_BANKING if banking else DEFAULT_SCOPE_TRADING)
    tokens_path = TOKENS_PATH_BANKING if banking else TOKENS_PATH_TRADING

    metadata = load_oauth_metadata()
    authorization_endpoint = str(metadata["authorization_endpoint"])
    token_endpoint = str(metadata["token_endpoint"])
    client = register_client(args.redirect_uri, banking=banking)

    if args.refresh:
        if not tokens_path.exists():
            print(f"No token file at {tokens_path}", file=sys.stderr)
            return 1
        saved = json.loads(tokens_path.read_text(encoding="utf-8"))
        refresh_token = saved.get("refresh_token")
        if not refresh_token:
            print(f"No refresh_token in {tokens_path.name} — run without --refresh", file=sys.stderr)
            return 1
        tokens = refresh_tokens(token_endpoint, client["client_id"], refresh_token)
        save_tokens(tokens, source="refresh_token", banking=banking)
        return 0

    print("Requirements before auth:")
    print("  • Primary Robinhood account in good standing")
    print("  • Desktop browser on your laptop (not AWS/Lightsail console browser)")
    if banking:
        print("  • Robinhood Gold + Agentic Credit Card onboarding (desktop)")
    else:
        print("  • Fund Agentic account when prompted during onboarding")
    if not args.manual:
        print("  • Tip: if you are on AWS SSH, use: python phase0_oauth.py --manual")
    print()

    if args.manual:
        tokens = manual_authorize(
            authorization_endpoint,
            token_endpoint,
            client["client_id"],
            args.redirect_uri,
            scope,
            callback_url=args.callback_url,
            banking=banking,
        )
    else:
        tokens = interactive_authorize(
            authorization_endpoint,
            token_endpoint,
            client["client_id"],
            args.redirect_uri,
            scope,
            banking=banking,
        )
    print(f"access_token={tokens.get('access_token', '')[:12]}…")
    if tokens.get("refresh_token"):
        print("refresh_token received")
    print("\nRun: python phase0_inventory.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
