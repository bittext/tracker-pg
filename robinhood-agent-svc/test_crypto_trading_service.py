"""Tests for Robinhood Crypto Trading API signing and normalization."""

from __future__ import annotations

import base64
from decimal import Decimal
from unittest.mock import MagicMock, patch

from nacl.signing import SigningKey

from crypto_trading_service import (
    RobinhoodCryptoTradingClient,
    _select_account_number,
    decode_ed25519_seed,
    normalize_base64,
    run_crypto_sync,
)


def test_auth_message_format() -> None:
    signing_key = SigningKey.generate()
    private_b64 = base64.b64encode(signing_key.encode()).decode("utf-8")
    client = RobinhoodCryptoTradingClient("test-api-key", private_b64)
    path = "/api/v2/crypto/trading/accounts/"
    ts = 1_700_000_000
    with patch.object(RobinhoodCryptoTradingClient, "_timestamp", return_value=ts):
        headers = client._auth_headers("GET", path)
    assert headers["x-api-key"] == "test-api-key"
    assert headers["x-timestamp"] == str(ts)
    assert headers["x-signature"]
    message = f"test-api-key{ts}{path}GET"
    signed = signing_key.sign(message.encode("utf-8"))
    assert headers["x-signature"] == base64.b64encode(signed.signature).decode("utf-8")
    client.close()


def test_select_account_number_prefers_active() -> None:
    acct = _select_account_number(
        [
            {"account_number": "111", "status": "inactive"},
            {"account_number": "222", "status": "active"},
        ]
    )
    assert acct == "222"


@patch("crypto_trading_service.RobinhoodCryptoTradingClient")
def test_run_crypto_sync_normalizes_holdings(mock_client_cls: MagicMock) -> None:
    mock_client = MagicMock()
    mock_client_cls.return_value = mock_client
    mock_client.list_accounts.return_value = [{"account_number": "999", "status": "active"}]
    mock_client.list_holdings.return_value = [
        {"asset_code": "BTC", "total_quantity": "0.5"},
        {"asset_code": "ETH", "total_quantity": "0"},
    ]
    mock_client.best_bid_ask.return_value = {"BTC-USD": Decimal("50000")}

    result = run_crypto_sync("key", base64.b64encode(SigningKey.generate().encode()).decode())

    assert result["ok"] is True
    assert result["account_number"] == "999"
    assert result["total_value"] == "25000.00"
    assert len(result["holdings"]) == 1
    assert result["holdings"][0]["symbol"] == "BTC"
    mock_client.close.assert_called_once()


def test_decode_ed25519_seed_rejects_api_key_length() -> None:
    import pytest

    with pytest.raises(ValueError, match="not the Robinhood API key"):
        decode_ed25519_seed("not-valid-base64!!!")


def test_normalize_base64_adds_padding() -> None:
    key = SigningKey.generate()
    raw = base64.b64encode(key.encode()).decode("utf-8").rstrip("=")
    assert len(normalize_base64(raw)) % 4 == 0
    assert decode_ed25519_seed(raw) == key.encode()
