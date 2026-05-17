# Robinhood notebooks (tracker-pg)

Interactive reporting for **Reports → Finance → Robinhood**. Data is always from **CSV import** in the app (no unofficial Robinhood API).

## Quick start (local JupyterLab)

```bash
# From repo root
docker compose -f docker-compose.robinhood-jupyter.yml up -d
# Open http://127.0.0.1:8888/lab  (token printed in container logs if RH_JUPYTER_TOKEN is unset)
```

1. In the web app, open **Reports → Finance → Robinhood**.
2. Use **Download bundle (JSON)** for your year/filter.
3. In JupyterLab, open a notebook under `notebooks/robinhood/`:
   - `robinhood_performance.ipynb` — monthly P&L bars
   - `robinhood_risk.ipynb` — drawdown, calendar heatmap, hold-time scatter, daily P&L distribution
4. Set `bundle_path` to the downloaded file and run all cells.

## Server-rendered HTML (optional)

Enable `TRACKER_FINANCE_ROBINHOOD_NOTEBOOK_SERVICE_ENABLED=true` and run `robinhood-notebook` from `docker-compose.stack.yml` (or `docker-compose.robinhood-jupyter.yml`). The Spring API calls `POST /v1/render` on the sidecar.

## Extend

- Add notebooks under this folder and register them in `robinhood-notebook-svc`.
- Reuse `lib/tracker_robinhood.py` for pandas helpers (`daily_pnl_frame`, `closed_trades_frame`, `calendar_pnl_matrix`, etc.).
- In the web UI, choose **Performance** or **Risk** before **Render notebook** when the sidecar is enabled.
