"""
Robinhood Agentic Trading sidecar for tracker-pg (Phase 1 read-only sync).

Endpoints
---------
- GET  /health
- POST /v1/sync  → {access_token, sync_default?} → accounts, portfolios, positions
"""

from __future__ import annotations

import logging
import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from sync_service import run_sync

LOGGER = logging.getLogger("robinhood-agent-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")

app = FastAPI(title="robinhood-agent-svc", version="1.0.0")


class SyncRequest(BaseModel):
    access_token: str = Field(min_length=10)
    sync_default: bool = True


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "robinhood-agent-svc"}


@app.post("/v1/sync")
def sync(body: SyncRequest) -> dict:
    try:
        return run_sync(body.access_token, sync_default=body.sync_default)
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("sync failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
