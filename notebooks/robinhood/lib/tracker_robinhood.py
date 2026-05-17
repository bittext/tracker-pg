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


def _report(bundle: dict[str, Any]) -> dict[str, Any]:
    return bundle.get("performanceReport") or {}


def transactions_frame(bundle: dict[str, Any]) -> pd.DataFrame:
    rows = bundle.get("transactions") or []
    if not rows:
        return pd.DataFrame()
    return pd.DataFrame(rows)


def performance_summary(bundle: dict[str, Any]) -> dict[str, Any]:
    return _report(bundle).get("summary") or {}


def monthly_pnl_frame(bundle: dict[str, Any]) -> pd.DataFrame:
    monthly = _report(bundle).get("monthlyPnL") or []
    if not monthly:
        return pd.DataFrame()
    return pd.DataFrame(monthly)


def daily_pnl_frame(bundle: dict[str, Any]) -> pd.DataFrame:
    daily = _report(bundle).get("dailyPnL") or []
    if not daily:
        return pd.DataFrame()
    df = pd.DataFrame(daily)
    if "date" in df.columns:
        df["date"] = pd.to_datetime(df["date"])
        df = df.set_index("date").sort_index()
    return df


def equity_curve_frame(bundle: dict[str, Any]) -> pd.DataFrame:
    curve = _report(bundle).get("equityCurve") or []
    if not curve:
        return pd.DataFrame()
    df = pd.DataFrame(curve)
    if "date" in df.columns:
        df["date"] = pd.to_datetime(df["date"])
        df = df.set_index("date").sort_index()
    if "cumulativePnL" in df.columns:
        df["cumulativePnL"] = pd.to_numeric(df["cumulativePnL"], errors="coerce").fillna(0.0)
    return df


def closed_trades_frame(bundle: dict[str, Any]) -> pd.DataFrame:
    rows = bundle.get("closedTrades")
    if not rows:
        insights = _report(bundle).get("insights") or {}
        rows = insights.get("worstTrades") or []
    if not rows:
        return pd.DataFrame()
    df = pd.DataFrame(rows)
    for col in ("realizedPnL", "quantity", "holdDays"):
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")
    for col in ("buyDate", "sellDate"):
        if col in df.columns:
            df[col] = pd.to_datetime(df[col], errors="coerce")
    return df


def drawdown_frame(equity: pd.DataFrame) -> pd.DataFrame:
    if equity.empty or "cumulativePnL" not in equity.columns:
        return pd.DataFrame()
    out = equity[["cumulativePnL"]].copy()
    out["peak"] = out["cumulativePnL"].cummax()
    out["drawdown"] = out["cumulativePnL"] - out["peak"]
    return out


def calendar_pnl_matrix(daily: pd.DataFrame) -> pd.DataFrame:
    """Week x weekday matrix of mean daily P&L for seaborn heatmap."""
    if daily.empty or "realizedPnL" not in daily.columns:
        return pd.DataFrame()
    s = daily["realizedPnL"].astype(float)
    cal = pd.DataFrame({"pnl": s, "week": s.index.isocalendar().week.astype(int), "weekday": s.index.weekday})
    pivot = cal.pivot_table(index="week", columns="weekday", values="pnl", aggfunc="sum", fill_value=0.0)
    pivot.columns = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][: len(pivot.columns)]
    return pivot


def pnl_distribution(daily: pd.DataFrame) -> pd.Series:
    if daily.empty or "realizedPnL" not in daily.columns:
        return pd.Series(dtype=float)
    return daily.loc[daily["realizedPnL"] != 0, "realizedPnL"].astype(float)


def max_drawdown(equity: pd.DataFrame) -> float:
    dd = drawdown_frame(equity)
    if dd.empty:
        return 0.0
    return float(dd["drawdown"].min())


def win_rate_closed(closed: pd.DataFrame) -> float | None:
    if closed.empty or "realizedPnL" not in closed.columns:
        return None
    pnl = closed["realizedPnL"]
    wins = (pnl > 0).sum()
    losses = (pnl < 0).sum()
    total = wins + losses
    return float(wins / total) if total else None
