"""Minimal Streamable HTTP MCP client for Robinhood Agentic Trading."""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any

import httpx

LOGGER = logging.getLogger(__name__)

MCP_ENDPOINT = "https://agent.robinhood.com/mcp/trading"
DEFAULT_PROTOCOL_VERSION = "2024-11-05"
MCP_ACCEPT = "application/json, text/event-stream"


@dataclass
class McpSession:
    session_id: str | None
    protocol_version: str


class RobinhoodMcpClient:
    """Streamable HTTP MCP client with optional Bearer auth."""

    def __init__(
        self,
        access_token: str | None = None,
        endpoint: str = MCP_ENDPOINT,
        timeout: float = 60.0,
    ) -> None:
        self.endpoint = endpoint
        self.access_token = access_token
        self.timeout = timeout
        self._session: McpSession | None = None
        self._request_id = 0

    def _headers(self, *, include_session: bool = True) -> dict[str, str]:
        headers = {
            "Content-Type": "application/json",
            "Accept": MCP_ACCEPT,
        }
        if self.access_token:
            headers["Authorization"] = f"Bearer {self.access_token}"
        if include_session and self._session and self._session.session_id:
            headers["Mcp-Session-Id"] = self._session.session_id
        return headers

    def _next_id(self) -> int:
        self._request_id += 1
        return self._request_id

    @staticmethod
    def _parse_body(response: httpx.Response) -> dict[str, Any] | list[Any] | None:
        content_type = response.headers.get("content-type", "")
        text = response.text.strip()
        if not text:
            return None
        if "text/event-stream" in content_type or text.startswith("event:"):
            return RobinhoodMcpClient._parse_sse_json(text)
        return json.loads(text)

    @staticmethod
    def _parse_sse_json(text: str) -> dict[str, Any] | None:
        for line in text.splitlines():
            if line.startswith("data:"):
                payload = line[5:].strip()
                if payload and payload != "[DONE]":
                    return json.loads(payload)
        return None

    def post(
        self,
        method: str,
        params: dict[str, Any] | None = None,
        *,
        notification: bool = False,
        include_session: bool = True,
    ) -> tuple[httpx.Response, dict[str, Any] | list[Any] | None]:
        body: dict[str, Any] = {
            "jsonrpc": "2.0",
            "method": method,
        }
        if not notification:
            body["id"] = self._next_id()
        if params is not None:
            body["params"] = params

        with httpx.Client(timeout=self.timeout) as client:
            response = client.post(
                self.endpoint,
                headers=self._headers(include_session=include_session),
                json=body,
            )
        parsed = None
        if response.content:
            try:
                parsed = self._parse_body(response)
            except json.JSONDecodeError as exc:
                LOGGER.warning("non-json response (%s): %s", response.status_code, response.text[:500])
                raise RuntimeError(f"MCP response not JSON: {exc}") from exc
        return response, parsed

    def initialize(self, protocol_version: str = DEFAULT_PROTOCOL_VERSION) -> dict[str, Any]:
        response, parsed = self.post(
            "initialize",
            {
                "protocolVersion": protocol_version,
                "capabilities": {},
                "clientInfo": {"name": "tracker-pg-phase0", "version": "0.1.0"},
            },
            include_session=False,
        )
        if response.status_code == 401:
            raise PermissionError("MCP initialize returned 401 — access token missing or expired")
        if response.status_code >= 400:
            raise RuntimeError(f"MCP initialize failed ({response.status_code}): {response.text}")

        session_id = response.headers.get("Mcp-Session-Id") or response.headers.get("mcp-session-id")
        self._session = McpSession(session_id=session_id, protocol_version=protocol_version)

        if not isinstance(parsed, dict):
            raise RuntimeError(f"unexpected initialize response: {parsed!r}")

        result = parsed.get("result")
        if parsed.get("error"):
            raise RuntimeError(f"MCP initialize error: {parsed['error']}")
        if not isinstance(result, dict):
            raise RuntimeError(f"initialize missing result: {parsed!r}")

        # Required lifecycle notification after initialize.
        self.post("notifications/initialized", {}, notification=True)
        return result

    def list_tools(self) -> list[dict[str, Any]]:
        response, parsed = self.post("tools/list", {})
        if response.status_code >= 400:
            raise RuntimeError(f"tools/list failed ({response.status_code}): {response.text}")
        if not isinstance(parsed, dict):
            raise RuntimeError(f"unexpected tools/list response: {parsed!r}")
        if parsed.get("error"):
            raise RuntimeError(f"tools/list error: {parsed['error']}")
        tools = parsed.get("result", {}).get("tools", [])
        if not isinstance(tools, list):
            raise RuntimeError(f"tools/list result.tools not a list: {tools!r}")
        return tools

    def call_tool(self, name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
        response, parsed = self.post(
            "tools/call",
            {"name": name, "arguments": arguments or {}},
        )
        if response.status_code >= 400:
            raise RuntimeError(f"tools/call {name!r} failed ({response.status_code}): {response.text}")
        if not isinstance(parsed, dict):
            raise RuntimeError(f"unexpected tools/call response: {parsed!r}")
        if parsed.get("error"):
            raise RuntimeError(f"tools/call error: {parsed['error']}")
        result = parsed.get("result")
        if not isinstance(result, dict):
            raise RuntimeError(f"tools/call missing result: {parsed!r}")
        return result

    def close_session(self) -> None:
        if not self._session or not self._session.session_id:
            return
        with httpx.Client(timeout=self.timeout) as client:
            client.delete(
                self.endpoint,
                headers=self._headers(include_session=True),
            )
        self._session = None
