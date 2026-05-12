# finbert-svc

Sidecar Python service used by **Finance → Trading → Predicts**. Wraps
[`ProsusAI/finbert`](https://huggingface.co/ProsusAI/finbert) (a BERT
fine‑tuned for financial sentiment) behind a tiny FastAPI process so the
Spring API can batch‑score social posts (StockTwits today, Reddit/X later).

The Java client (`FinbertSentimentClient`) calls `POST /score`. When this
container is unreachable, the Java side automatically falls back to a
lightweight bullish/bearish heuristic so Predicts ingestion never blocks.

## Endpoints

- `GET  /health` — `{status, model, loaded, load_error, device}`
- `POST /score`  — body `{texts: ["..."]}`; response includes per‑text
  `{label, positive, negative, neutral, score, confidence}` where
  `score = positive − negative` is a polarity in `[-1, +1]`.

## Run locally

```bash
cd finbert-svc
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

First call downloads the model (~440 MB). Cache it by setting `HF_HOME` to a
persistent path or by using the `finbert_cache` volume mounted in
`docker-compose.stack.yml`.

## Run as part of the stack

```bash
docker compose --env-file .env.stack -f docker-compose.stack.yml \
  up -d --build finbert
docker compose --env-file .env.stack -f docker-compose.stack.yml logs -f finbert
```

The Spring side picks it up automatically via
`TRACKER_FINANCE_PREDICTS_FINBERT_BASE_URL=http://finbert:8000` (default in
the stack compose). To disable:

```
TRACKER_FINANCE_PREDICTS_FINBERT_ENABLED=false
```

…and the Java fallback heuristic takes over.

## Configuration

| Env var                | Default               | Purpose                                  |
| ---------------------- | --------------------- | ---------------------------------------- |
| `FINBERT_MODEL`        | `ProsusAI/finbert`    | Hugging Face model id.                   |
| `FINBERT_MAX_BATCH`    | `64`                  | Max texts per request.                   |
| `FINBERT_MAX_TOKENS`   | `256`                 | Truncate inputs to this many tokens.     |
| `FINBERT_DEVICE`       | `cpu`                 | `cpu` or `cuda` (image is CPU‑only).     |

## Memory footprint

On CPU the model uses roughly 600 MB resident; add 150 MB headroom for batch
encoding. Plan ≥ 1 GB RAM allocated to this container in production. On a
constrained host you can run only one worker (`--workers 1`, the default
here) and reduce `FINBERT_MAX_BATCH` to 16.
