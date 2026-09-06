"""Tests for Agentic MCP crypto order helpers."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

import crypto_mcp_order_service as svc


def test_asset_code_strips_usd_suffix() -> None:
    assert svc._asset_code("sol-usd") == "SOL"
    assert svc._asset_code("XRP") == "XRP"
    assert svc._asset_code("DOGEUSD") == "DOGE"


def test_order_args_sell_all_uses_transferable_quantity() -> None:
    client = MagicMock()
    client.call_tool.return_value = {
        "data": {
            "results": [
                {
                    "currency": {"code": "SOL"},
                    "quantity": "0.000107455",
                    "quantity_transferable": "0.000107455",
                }
            ]
        }
    }
    args = svc._order_args(
        {"symbol": "SOL", "side": "sell", "type": "market", "sell_all": True},
        "799863550",
        client,
    )
    assert args["quantity"] == "0.000107455"
    assert "dollar_amount" not in args
    client.call_tool.assert_called_once_with(
        "get_crypto_positions", {"rhs_account_number": "799863550"}
    )


def test_order_args_rejects_both_quantity_and_amount() -> None:
    try:
        svc._order_args(
            {"symbol": "XRP", "side": "buy", "type": "market", "quantity": "1", "amount": "10"},
            "799863550",
            MagicMock(),
        )
    except ValueError as exc:
        assert "exactly one" in str(exc)
    else:
        raise AssertionError("expected ValueError")


@patch("crypto_mcp_order_service.RobinhoodMcpClient")
def test_review_resolves_agentic_rhs_account(mock_client_cls: MagicMock) -> None:
    client = mock_client_cls.return_value
    client.call_tool.side_effect = [
        {
            "data": {
                "accounts": [
                    {
                        "account_number": "799863550",
                        "rhs_account_number": "799863550",
                        "rhc_account_number": "311263615767",
                        "agentic_allowed": True,
                    }
                ]
            }
        },
        {"estimated_cost": "10.33"},
    ]
    result = svc.run_crypto_mcp_review(
        "token",
        {"symbol": "SOL", "side": "buy", "type": "market", "quantity": "0.0001"},
    )
    assert result["ok"] is True
    assert result["account_number"] == "799863550"
    assert result["asset_class"] == "crypto"
    assert result["order_args"]["rhs_account_number"] == "799863550"
    preview_call = client.call_tool.call_args_list[1]
    assert preview_call.args[0] == "preview_crypto_order"
