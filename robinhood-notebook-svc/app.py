"""
Robinhood notebook render sidecar for tracker-pg Reports → Finance → Robinhood.

Accepts the same JSON bundle as GET /api/finance/robinhood/notebook-bundle, executes
``notebooks/robinhood/robinhood_performance.ipynb`` with papermill, and returns HTML via nbconvert.

Endpoints
---------
- GET  /health     → {status, notebook}
- POST /v1/render  → bundle JSON → {html, source, note}
"""

from __future__ import annotations

import json
import logging
import os
import tempfile
import uuid
from pathlib import Path
from typing import Any, Optional

import papermill as pm
from fastapi import FastAPI, HTTPException
from nbconvert import HTMLExporter
import nbformat

LOGGER = logging.getLogger("robinhood-notebook-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")

NOTEBOOK_DIR = Path(os.environ.get("RH_NOTEBOOK_DIR", "/app/notebooks/robinhood"))
INPUT_NOTEBOOK = NOTEBOOK_DIR / "robinhood_performance.ipynb"
MAX_BUNDLE_BYTES = int(os.environ.get("RH_MAX_BUNDLE_BYTES", str(32 * 1024 * 1024)))

app = FastAPI(title="robinhood-notebook-svc", version="1.0.0")


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok" if INPUT_NOTEBOOK.is_file() else "degraded",
        "notebook": str(INPUT_NOTEBOOK),
        "notebook_exists": INPUT_NOTEBOOK.is_file(),
    }


@app.post("/v1/render")
def render(bundle: dict[str, Any]) -> dict:
    if not INPUT_NOTEBOOK.is_file():
        raise HTTPException(status_code=503, detail=f"notebook missing: {INPUT_NOTEBOOK}")
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
            str(INPUT_NOTEBOOK),
            str(executed),
            parameters={"bundle_path": str(bundle_path), "year": year},
            cwd=str(NOTEBOOK_DIR),
            log_output=True,
        )
        nb = nbformat.read(executed, as_version=4)
        html, _ = HTMLExporter(template_name="classic").from_notebook_node(nb)
        note = bundle.get("usageNote") or "Rendered with papermill + nbconvert."
        return {"html": html, "source": "papermill", "note": note}
    except Exception as exc:
        LOGGER.exception("notebook render failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    finally:
        try:
            for p in work.iterdir():
                p.unlink(missing_ok=True)
            work.rmdir()
        except OSError:
            pass
