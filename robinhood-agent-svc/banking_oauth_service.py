"""OAuth token refresh for Robinhood Agentic Banking MCP (credit-card scope)."""

from __future__ import annotations

from typing import Any

import httpx

BANKING_REGISTER_ENDPOINT = "https://banking-agent.robinhood.com/oauth/banking/register"
TOKEN_ENDPOINT = "https://api.robinhood.com/oauth2/token/"
DEFAULT_REDIRECT = "http://127.0.0.1:8765/callback"


def register_banking_client(*, redirect_uri: str = DEFAULT_REDIRECT) -> str:
    payload = {
        "client_name": "tracker-pg-robinhood-banking-svc",
        "redirect_uris": [redirect_uri],
        "grant_types": ["authorization_code", "refresh_token"],
        "response_types": ["code"],
        "token_endpoint_auth_method": "none",
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(BANKING_REGISTER_ENDPOINT, json=payload)
        response.raise_for_status()
        body = response.json()
    if not isinstance(body, dict) or "client_id" not in body:
        raise RuntimeError(f"banking registration failed: {body!r}")
    return str(body["client_id"])


def refresh_banking_access_token(refresh_token: str, *, client_id: str | None = None) -> dict[str, Any]:
    resolved_client_id = client_id or register_banking_client()
    data = {
        "grant_type": "refresh_token",
        "client_id": resolved_client_id,
        "refresh_token": refresh_token,
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(TOKEN_ENDPOINT, data=data)
        if response.status_code >= 400:
            raise PermissionError(f"banking refresh failed ({response.status_code}): {response.text}")
        body = response.json()
    if not isinstance(body, dict):
        raise RuntimeError("banking refresh response not an object")
    return body
