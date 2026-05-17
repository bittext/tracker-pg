# robinhood-notebook-svc

FastAPI sidecar that executes `notebooks/robinhood/robinhood_performance.ipynb` with [papermill](https://papermill.readthedocs.io/) and returns HTML via nbconvert.

Called by the Spring API when `TRACKER_FINANCE_ROBINHOOD_NOTEBOOK_SERVICE_ENABLED=true`.

## Build

```bash
docker build -f robinhood-notebook-svc/Dockerfile .
```

## Endpoints

- `GET /health`
- `POST /v1/render` — body: same JSON as `GET /api/finance/robinhood/notebook-bundle`
