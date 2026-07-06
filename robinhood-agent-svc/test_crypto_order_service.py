"""Tests for crypto POST order signing and placement."""

from __future__ import annotations

import base64
import json
from decimal import Decimal
from unittest.mock import MagicMock, patch

from nacl.signing import SigningKey

from crypto_trading_service import RobinhoodCryptoTradingClient


def test_post_auth_message_includes_body() -> None:
    signing_key = SigningKey.generate()
    private_b64 = base64.b64encode(signing_key.encode()).decode("utf-8")
    client = RobinhoodCryptoTradingClient("test-api-key", private_b64)
    path = "/api/v2/crypto/trading/orders/?account_number=123"
    body = '{"side":"buy","type":"market"}'
    ts = 1_700_000_001
    with patch.object(RobinhoodCryptoTradingClient, "_timestamp", return_value=ts):
        headers = client._auth_headers("POST", path, body)
    message = f"test-api-key{ts}{path}POST{body}"
    signed = signing_key.sign(message.encode("utf-8"))
    assert headers["x-signature"] == base64.b64encode(signed.signature).decode("utf-8")
    client.close()


@patch("crypto_trading_service.RobinhoodCryptoTradingClient._post")
@patch("crypto_trading_service.RobinhoodCryptoTradingClient._ask_price")
def test_place_market_order_buy_quote_amount(mock_ask: MagicMock, mock_post: MagicMock) -> None:
    signing_key = SigningKey.generate()
    private_b64 = base64.b64encode(signing_key.encode()).decode("utf-8")
    client = RobinhoodCryptoTradingClient("key", private_b64)
    mock_post.return_value = {"id": "ord-1", "state": "submitted"}
    mock_ask.return_value = Decimal("2500.00")

    result = client.place_market_order(
        "acct-99", "BTC", "buy", quote_amount="25.00", client_order_id="cid-1"
    )

    assert result["id"] == "ord-1"
    mock_post.assert_called_once()
    path, body_str = mock_post.call_args[0]
    assert "account_number=acct-99" in path
    body = json.loads(body_str)
    assert body["symbol"] == "BTC-USD"
    assert body["side"] == "buy"
    assert body["type"] == "market"
    assert body["client_order_id"] == "cid-1"
    assert body["market_order_config"]["asset_quantity"] == "0.01"
    assert "quote_amount" not in body["market_order_config"]
    client.close()
