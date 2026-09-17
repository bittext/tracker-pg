"""Parse helpers for Robinhood MCP get_realized_pnl."""

from decimal import Decimal

from realized_pnl_service import last4, total_and_trades


def test_last4_strips_non_digits() -> None:
    assert last4("561723370") == "3370"
    assert last4("••••3550") == "3550"


def test_total_prefers_window_total_returns() -> None:
    total, trades = total_and_trades(
        {
            "data": {
                "total_returns": "258511.27",
                "data_points": [
                    {"realized_gain": "100", "number_of_trades": 2},
                    {"realized_gain": None, "number_of_trades": 0},
                    {"realized_gain": "50.27", "number_of_trades": 1},
                ],
            }
        }
    )
    assert total == Decimal("258511.27")
    assert trades == 3


def test_total_falls_back_to_bucket_sum() -> None:
    total, trades = total_and_trades(
        {
            "data": {
                "data_points": [
                    {"realized_gain": "3593", "number_of_trades": 1},
                    {"realized_gain": "-695", "number_of_trades": 1},
                ]
            }
        }
    )
    assert total == Decimal("2898")
    assert trades == 2
