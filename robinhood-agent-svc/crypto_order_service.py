"""Robinhood Crypto Trading API market order placement."""

from __future__ import annotations

from typing import Any

from crypto_trading_service import RobinhoodCryptoTradingClient


def run_crypto_place_order(
    api_key: str,
    private_key_base64: str,
    account_number: str,
    symbol: str,
    side: str,
    *,
    asset_quantity: str | None = None,
    quote_amount: str | None = None,
    client_order_id: str | None = None,
) -> dict[str, Any]:
    client = RobinhoodCryptoTradingClient(api_key, private_key_base64)
    try:
        result = client.place_market_order(
            account_number,
            symbol,
            side,
            asset_quantity=asset_quantity,
            quote_amount=quote_amount,
            client_order_id=client_order_id,
        )
        order_id = None
        state = None
        if isinstance(result, dict):
            order_id = result.get("id") or result.get("order_id")
            state = result.get("state") or result.get("status")
        return {
            "ok": True,
            "message": "Crypto order submitted.",
            "client_order_id": result.get("client_order_id") if isinstance(result, dict) else client_order_id,
            "order_id": str(order_id) if order_id else None,
            "state": str(state) if state else "submitted",
            "symbol": result.get("symbol") if isinstance(result, dict) else symbol,
            "side": side,
            "result": result,
        }
    finally:
        client.close()
