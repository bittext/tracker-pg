"""Webull market-data sidecar for tracker-pg RH holdings live prices."""

from __future__ import annotations

import logging
import os
from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from quote_service import run_quotes

LOGGER = logging.getLogger("webull-quote-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")

app = FastAPI(title="webull-quote-svc", version="1.0.0")


class OptionQuoteRequest(BaseModel):
    instrument_id: str = Field(min_length=1)
    symbol: str = Field(min_length=1)
    strike: float | int | str
    expiration: str = Field(min_length=4)
    option_type: str = Field(min_length=1)


class QuotesRequest(BaseModel):
    symbols: list[str] = Field(default_factory=list)
    options: list[OptionQuoteRequest] = Field(default_factory=list)


@app.get("/health")
def health() -> dict[str, str]:
    user_id = os.environ.get("WEBULL_USER_ID", "").strip() or os.environ.get("WEBULL_APP_KEY", "").strip()
    app_secret = os.environ.get("WEBULL_APP_SECRET", "").strip() or os.environ.get(
        "WEBULL_APP_KEY_SECRET", ""
    ).strip()
    configured = bool(user_id) and bool(app_secret)
    return {
        "status": "ok" if configured else "misconfigured",
        "service": "webull-quote-svc",
        "credentials": "present" if configured else "missing",
    }


@app.post("/v1/quotes")
def quotes(body: QuotesRequest) -> dict[str, Any]:
    try:
        return run_quotes(
            symbols=body.symbols,
            options=[row.model_dump() for row in body.options],
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("quotes failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
