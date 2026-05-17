"""Helpers for Robinhood Jupyter workflows (load tracker export bundles)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pandas as pd


def load_bundle(path: str | Path) -> dict[str, Any]:
    p = Path(path)
    with p.open(encoding="utf-8") as f:
        return json.load(f)


def transactions_frame(bundle: dict[str, Any]) -> pd.DataFrame:
    rows = bundle.get("transactions") or []
    if not rows:
        return pd.DataFrame()
    return pd.DataFrame(rows)


def performance_summary(bundle: dict[str, Any]) -> dict[str, Any]:
    report = bundle.get("performanceReport") or {}
    return report.get("summary") or {}


def monthly_pnl_frame(bundle: dict[str, Any]) -> pd.DataFrame:
    report = bundle.get("performanceReport") or {}
    monthly = report.get("monthlyPnL") or []
    if not monthly:
        return pd.DataFrame()
    return pd.DataFrame(monthly)
