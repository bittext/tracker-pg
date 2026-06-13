"""OAuth token refresh for Robinhood Agentic MCP (Phase 2)."""

from __future__ import annotations

from typing import Any

import httpx

OAUTH_AS_METADATA = "https://agent.robinhood.com/.well-known/oauth-authorization-server"
REGISTER_ENDPOINT = "https://agent.robinhood.com/oauth/trading/register"
DEFAULT_REDIRECT = "http://127.0.0.1:8765/callback"


def load_oauth_metadata() -> dict[str, Any]:
    with httpx.Client(timeout=30.0) as client:
        response = client.get(OAUTH_AS_METADATA)
        response.raise_for_status()
        body = response.json()
    if not isinstance(body, dict):
        raise RuntimeError("unexpected oauth metadata")
    return body


def register_client(*, redirect_uri: str = DEFAULT_REDIRECT) -> str:
    """Dynamic client registration; returns client_id."""
    payload = {
        "client_name": "tracker-pg-robinhood-agent-svc",
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
    return str(body["client_id"])


def refresh_access_token(refresh_token: str, *, client_id: str | None = None) -> dict[str, Any]:
    metadata = load_oauth_metadata()
    token_endpoint = str(metadata["token_endpoint"])
    resolved_client_id = client_id or register_client()
    data = {
        "grant_type": "refresh_token",
        "client_id": resolved_client_id,
        "refresh_token": refresh_token,
    }
    with httpx.Client(timeout=30.0) as client:
        response = client.post(token_endpoint, data=data)
        if response.status_code >= 400:
            raise PermissionError(f"refresh failed ({response.status_code}): {response.text}")
        body = response.json()
    if not isinstance(body, dict):
        raise RuntimeError("refresh response not an object")
    return body
