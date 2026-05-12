"""
FinBERT sentiment sidecar for tracker-pg Finance → Trading → Predicts.

Wraps Hugging Face `ProsusAI/finbert` (financial-domain BERT classifier) behind a
tiny FastAPI service. The Spring server (`FinbertSentimentClient`) calls
`POST /score` in small batches every poll cycle and falls back to a built-in
heuristic when this container is unavailable, so this service is best-effort.

Endpoints
---------
- GET  /health   → {status:"ok", model:..., loaded:bool}
- POST /score    → batch scoring: {texts:[...]}  → {scores:[{label,positive,negative,neutral,score,confidence}, ...]}

Configuration (env)
-------------------
- FINBERT_MODEL          : Hugging Face model id (default ProsusAI/finbert)
- FINBERT_MAX_BATCH      : max texts per request (default 64)
- FINBERT_MAX_TOKENS     : truncation length (default 256)
- FINBERT_DEVICE         : "cpu" or "cuda" (default cpu)
"""

from __future__ import annotations

import logging
import os
from typing import List, Optional

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoModelForSequenceClassification, AutoTokenizer

LOGGER = logging.getLogger("finbert-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s :: %(message)s")

MODEL_NAME = os.environ.get("FINBERT_MODEL", "ProsusAI/finbert")
MAX_BATCH = int(os.environ.get("FINBERT_MAX_BATCH", "64"))
MAX_TOKENS = int(os.environ.get("FINBERT_MAX_TOKENS", "256"))
DEVICE = os.environ.get("FINBERT_DEVICE", "cpu")

LABELS = ["positive", "negative", "neutral"]

app = FastAPI(title="finbert-svc", version="1.0.0")

_state = {"tokenizer": None, "model": None, "loaded": False, "load_error": None}


def _load_model() -> None:
    if _state["loaded"]:
        return
    LOGGER.info("loading model=%s device=%s", MODEL_NAME, DEVICE)
    try:
        tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
        model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME)
        model.to(DEVICE)
        model.eval()
        _state["tokenizer"] = tokenizer
        _state["model"] = model
        _state["loaded"] = True
        LOGGER.info("model ready: %s", MODEL_NAME)
    except Exception as exc:
        _state["load_error"] = str(exc)
        LOGGER.exception("failed to load model")


@app.on_event("startup")
def _on_startup() -> None:
    _load_model()


class ScoreRequest(BaseModel):
    texts: List[str] = Field(..., description="Raw social posts; whitespace-trimmed, truncated to MAX_TOKENS.")


class ScoreResult(BaseModel):
    label: str
    positive: float
    negative: float
    neutral: float
    score: float
    confidence: float


class ScoreResponse(BaseModel):
    model: str
    scores: List[ScoreResult]
    load_error: Optional[str] = None


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok" if _state["loaded"] else "degraded",
        "model": MODEL_NAME,
        "loaded": _state["loaded"],
        "load_error": _state["load_error"],
        "device": DEVICE,
    }


@app.post("/score", response_model=ScoreResponse)
def score(req: ScoreRequest) -> ScoreResponse:
    if not _state["loaded"]:
        # Attempt one lazy reload (handles initial Hugging Face hub stalls)
        _load_model()
    if not _state["loaded"]:
        raise HTTPException(status_code=503, detail=f"model not loaded: {_state['load_error']}")
    if not req.texts:
        return ScoreResponse(model=MODEL_NAME, scores=[])
    if len(req.texts) > MAX_BATCH:
        raise HTTPException(status_code=413, detail=f"batch too large (>{MAX_BATCH})")

    tokenizer = _state["tokenizer"]
    model = _state["model"]
    cleaned = [(t or "").strip() for t in req.texts]
    encoded = tokenizer(
        cleaned,
        padding=True,
        truncation=True,
        max_length=MAX_TOKENS,
        return_tensors="pt",
    )
    encoded = {k: v.to(DEVICE) for k, v in encoded.items()}

    with torch.no_grad():
        logits = model(**encoded).logits
        probs = torch.softmax(logits, dim=1).cpu().tolist()

    # The HF FinBERT label order is [positive, negative, neutral]; rely on `id2label`.
    id2label = {int(k): v.lower() for k, v in model.config.id2label.items()}
    out: list[ScoreResult] = []
    for row in probs:
        label_probs = {id2label[i]: float(row[i]) for i in range(len(row))}
        positive = label_probs.get("positive", 0.0)
        negative = label_probs.get("negative", 0.0)
        neutral = label_probs.get("neutral", 0.0)
        top_label = max(label_probs, key=label_probs.get)
        # Net polarity score in [-1, +1], common downstream convention.
        score_value = positive - negative
        confidence = max(positive, negative, neutral)
        out.append(
            ScoreResult(
                label=top_label,
                positive=positive,
                negative=negative,
                neutral=neutral,
                score=score_value,
                confidence=confidence,
            )
        )
    return ScoreResponse(model=MODEL_NAME, scores=out)
