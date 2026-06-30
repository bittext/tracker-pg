"""Build OCC option symbols for Webull OpenAPI (compact form, e.g. AAPL260522C00300000)."""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal, InvalidOperation


def _parse_expiration(expiration: str) -> str:
    text = (expiration or "").strip()
    if not text:
        raise ValueError("expiration is required")
    if len(text) >= 10 and text[4] == "-":
        return text[2:4] + text[5:7] + text[8:10]
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%m/%d/%Y"):
        try:
            return datetime.strptime(text[:10], fmt).strftime("%y%m%d")
        except ValueError:
            continue
    if len(text) == 6 and text.isdigit():
        return text
    raise ValueError(f"unsupported expiration format: {expiration}")


def _option_side(option_type: str) -> str:
    text = (option_type or "").strip().lower()
    if text in {"call", "c"}:
        return "C"
    if text in {"put", "p"}:
        return "P"
    raise ValueError(f"unsupported option_type: {option_type}")


def _strike_field(strike: float | int | str | Decimal) -> str:
    try:
        value = Decimal(str(strike))
    except (InvalidOperation, ValueError) as exc:
        raise ValueError(f"invalid strike: {strike}") from exc
    scaled = int((value * Decimal(1000)).quantize(Decimal("1")))
    if scaled < 0:
        raise ValueError(f"invalid strike: {strike}")
    return f"{scaled:08d}"


def build_occ_symbol(
    symbol: str,
    expiration: str,
    strike: float | int | str | Decimal,
    option_type: str,
) -> str:
    root = (symbol or "").strip().upper()
    if not root:
        raise ValueError("symbol is required")
    return f"{root}{_parse_expiration(expiration)}{_option_side(option_type)}{_strike_field(strike)}"


def parse_expiration_date(expiration: str) -> date | None:
    text = (expiration or "").strip()
    if not text:
        return None
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%m/%d/%Y"):
        try:
            return datetime.strptime(text[:10], fmt).date()
        except ValueError:
            continue
    return None
