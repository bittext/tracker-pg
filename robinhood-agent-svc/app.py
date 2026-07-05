"""
Robinhood Agentic Trading sidecar for tracker-pg.

Endpoints
---------
- GET  /health
- POST /v1/sync           → {access_token, sync_default?, sync_all?}
- POST /v1/refresh-token  → {refresh_token, client_id?}
- POST /v1/review-order   → {access_token, symbol, side, type, ...}
- POST /v1/place-order    → {access_token, symbol, side, type, ...}
- POST /v1/quotes         → {access_token, symbols?, option_instrument_ids?}
- POST /v1/banking/sync   → {access_token, transaction_limit?}
- POST /v1/banking/refresh-token → {refresh_token, client_id?}
- POST /v1/crypto/sync     → {api_key, private_key_base64}
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from oauth_service import refresh_access_token
from banking_oauth_service import refresh_banking_access_token
from banking_sync_service import run_banking_sync
from order_service import run_place, run_review
from quote_service import run_quotes
from sync_service import run_sync
from crypto_trading_service import run_crypto_sync

LOGGER = logging.getLogger("robinhood-agent-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")

app = FastAPI(title="robinhood-agent-svc", version="2.0.0")


class SyncRequest(BaseModel):
    access_token: str = Field(min_length=10)
    sync_default: bool = True
    sync_all: bool = True


class RefreshTokenRequest(BaseModel):
    refresh_token: str = Field(min_length=10)
    client_id: str | None = None


class OrderRequest(BaseModel):
    access_token: str = Field(min_length=10)
    account_number: str | None = None
    symbol: str = Field(min_length=1)
    side: str = Field(min_length=2)
    type: str = Field(default="market")
    quantity: str | float | int | None = None
    amount: str | float | int | None = None
    limit_price: str | float | int | None = None
    time_in_force: str | None = None


class QuotesRequest(BaseModel):
    access_token: str = Field(min_length=10)
    symbols: list[str] = Field(default_factory=list)
    option_instrument_ids: list[str] = Field(default_factory=list)


class BankingSyncRequest(BaseModel):
    access_token: str = Field(min_length=10)
    transaction_limit: int = Field(default=20, ge=1, le=50)


class BankingRefreshTokenRequest(BaseModel):
    refresh_token: str = Field(min_length=10)
    client_id: str | None = None


class CryptoSyncRequest(BaseModel):
    api_key: str = Field(min_length=8)
    private_key_base64: str = Field(min_length=16)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "robinhood-agent-svc", "phase": "2"}


@app.post("/v1/sync")
def sync(body: SyncRequest) -> dict:
    try:
        return run_sync(body.access_token, sync_default=body.sync_default, sync_all=body.sync_all)
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("sync failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/v1/refresh-token")
def refresh_token(body: RefreshTokenRequest) -> dict[str, Any]:
    try:
        tokens = refresh_access_token(body.refresh_token, client_id=body.client_id)
        return {"ok": True, **tokens}
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("refresh failed")
        raise HTTPException(status_code=502, detail=str(exc)) from exc


def _order_body(body: OrderRequest) -> dict[str, Any]:
    return body.model_dump(exclude={"access_token"}, exclude_none=True)


@app.post("/v1/crypto/sync")
def crypto_sync(body: CryptoSyncRequest) -> dict:
    try:
        return run_crypto_sync(body.api_key, body.private_key_base64)
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("crypto sync failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/v1/banking/sync")
def banking_sync(body: BankingSyncRequest) -> dict:
    try:
        return run_banking_sync(body.access_token, transaction_limit=body.transaction_limit)
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("banking sync failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/v1/banking/refresh-token")
def banking_refresh_token(body: BankingRefreshTokenRequest) -> dict[str, Any]:
    try:
        tokens = refresh_banking_access_token(body.refresh_token, client_id=body.client_id)
        return {"ok": True, **tokens}
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("banking refresh failed")
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/v1/quotes")
def quotes(body: QuotesRequest) -> dict[str, Any]:
    try:
        return run_quotes(
            body.access_token,
            symbols=body.symbols,
            option_instrument_ids=body.option_instrument_ids,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("quotes failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/v1/review-order")
def review_order(body: OrderRequest) -> dict:
    try:
        return run_review(body.access_token, _order_body(body))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("review-order failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/v1/place-order")
def place_order(body: OrderRequest) -> dict:
    try:
        return run_place(body.access_token, _order_body(body))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        LOGGER.exception("place-order failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
