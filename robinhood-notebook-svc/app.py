"""
Robinhood notebook render sidecar for tracker-pg Reports → Finance → Robinhood.

Executes parameterized notebooks under ``notebooks/robinhood`` with papermill and returns HTML via nbconvert.

Endpoints
---------
- GET  /health     → {status, notebooks}
- POST /v1/render  → bundle JSON (+ optional ``notebook``: performance | risk) → {html, source, note}
"""

from __future__ import annotations

import json
import logging
import os
import tempfile
from pathlib import Path
from typing import Any

import papermill as pm
from fastapi import FastAPI, HTTPException, Query, Request
from nbconvert import HTMLExporter
import nbformat

LOGGER = logging.getLogger("robinhood-notebook-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")

NOTEBOOK_DIR = Path(os.environ.get("RH_NOTEBOOK_DIR", "/app/notebooks/robinhood"))
NOTEBOOK_FILES: dict[str, str] = {
    "performance": "robinhood_performance.ipynb",
    "risk": "robinhood_risk.ipynb",
}
MAX_BUNDLE_BYTES = int(os.environ.get("RH_MAX_BUNDLE_BYTES", str(32 * 1024 * 1024)))

app = FastAPI(title="robinhood-notebook-svc", version="1.2.0")


def _resolve_notebook(notebook_id: str) -> Path:
    key = (notebook_id or "performance").strip().lower()
    filename = NOTEBOOK_FILES.get(key)
    if not filename:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown notebook '{notebook_id}'. Use: {', '.join(NOTEBOOK_FILES)}",
        )
    path = NOTEBOOK_DIR / filename
    if not path.is_file():
        raise HTTPException(status_code=503, detail=f"notebook missing: {path}")
    return path


def _bundle_from_payload(payload: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    notebook = str(payload.get("notebook", "performance"))
    bundle = {k: v for k, v in payload.items() if k != "notebook"}
    return notebook, bundle


async def _read_raw_body(request: Request) -> bytes:
    raw_bytes = await request.body()
    if raw_bytes:
        return raw_bytes
    cl = request.headers.get("content-length", "").strip()
    expect = (request.headers.get("expect") or "").lower()
    if (cl.isdigit() and int(cl) > 0) or expect == "100-continue":
        chunks: list[bytes] = []
        while True:
            message = await request.receive()
            if message["type"] != "http.request":
                continue
            part = message.get("body") or b""
            if part:
                chunks.append(part)
            if not message.get("more_body", False):
                break
        raw_bytes = b"".join(chunks)
    if not raw_bytes:
        stream_chunks: list[bytes] = []
        async for chunk in request.stream():
            if chunk:
                stream_chunks.append(chunk)
        raw_bytes = b"".join(stream_chunks)
    return raw_bytes


async def _read_json_object(request: Request) -> dict[str, Any]:
    raw_bytes = await _read_raw_body(request)
    if not raw_bytes:
        LOGGER.warning(
            "empty POST body method=%s path=%s content-type=%s content-length=%s expect=%s",
            request.method,
            request.url.path,
            request.headers.get("content-type"),
            request.headers.get("content-length"),
            request.headers.get("expect"),
        )
        raise HTTPException(status_code=422, detail="JSON request body required")
    LOGGER.info("received JSON body bytes=%s path=%s", len(raw_bytes), request.url.path)
    try:
        payload = json.loads(raw_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise HTTPException(status_code=422, detail="Invalid JSON request body") from exc
    if not isinstance(payload, dict):
        raise HTTPException(status_code=422, detail="Request body must be a JSON object")
    return payload


def _render_notebook(notebook_id: str, bundle: dict[str, Any]) -> dict:
    input_path = _resolve_notebook(notebook_id)
    raw = json.dumps(bundle, default=str)
    if len(raw.encode("utf-8")) > MAX_BUNDLE_BYTES:
        raise HTTPException(status_code=413, detail="bundle too large")

    work = Path(tempfile.mkdtemp(prefix="rh-nb-"))
    bundle_path = work / "bundle.json"
    executed = work / "executed.ipynb"
    bundle_path.write_text(raw, encoding="utf-8")

    year = bundle.get("year")
    try:
        pm.execute_notebook(
            str(input_path),
            str(executed),
            parameters={"bundle_path": str(bundle_path), "year": year},
            cwd=str(NOTEBOOK_DIR),
            log_output=True,
        )
        nb = nbformat.read(executed, as_version=4)
        html, _ = HTMLExporter(template_name="classic").from_notebook_node(nb)
        note = bundle.get("usageNote") or f"Rendered {notebook_id} with papermill + nbconvert."
        return {"html": html, "source": "papermill", "note": note, "notebook": notebook_id}
    except Exception as exc:
        LOGGER.exception("notebook render failed notebook=%s", notebook_id)
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    finally:
        try:
            for p in work.iterdir():
                p.unlink(missing_ok=True)
            work.rmdir()
        except OSError:
            pass


@app.get("/health")
def health() -> dict:
    notebooks = {
        key: {"path": str(NOTEBOOK_DIR / name), "exists": (NOTEBOOK_DIR / name).is_file()}
        for key, name in NOTEBOOK_FILES.items()
    }
    ok = all(v["exists"] for v in notebooks.values())
    return {"status": "ok" if ok else "degraded", "version": "1.4.1", "notebooks": notebooks}


@app.post("/v1/render")
async def render(request: Request, notebook: str = Query("performance")) -> dict:
    """Notebook id as query param; JSON bundle in body (used by Spring api)."""
    bundle = await _read_json_object(request)
    notebook_id = (notebook or "performance").strip().lower()
    return _render_notebook(notebook_id, bundle)


@app.post("/v1/render/{notebook_id}")
async def render_with_notebook_path(notebook_id: str, request: Request) -> dict:
    """Alternate: notebook id in URL path."""
    bundle = await _read_json_object(request)
    return _render_notebook(notebook_id.strip().lower(), bundle)
