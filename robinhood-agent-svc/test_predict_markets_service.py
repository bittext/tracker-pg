from decimal import Decimal

from predict_markets_service import (
    collapse_cumulative_closes,
    history_label,
    is_event_trade,
    parse_event_closes,
    parse_open_cost,
    parse_open_value,
)


def test_is_event_trade_blank_symbol_and_side() -> None:
    assert is_event_trade({"symbol": "", "side": "", "quantity": "87", "realized_gain": "-36.77"})
    assert not is_event_trade({"symbol": "HOOD", "side": "sell", "quantity": "1"})


def test_is_event_trade_asset_type() -> None:
    assert is_event_trade({"symbol": "HOU", "side": "buy", "asset_type": "event_contract"})


def test_parse_event_closes_skips_equities() -> None:
    rows, cursor = parse_event_closes(
        {
            "data": {
                "trades": [
                    {
                        "timestamp": "2026-10-06T01:10:57Z",
                        "symbol": "",
                        "side": "",
                        "quantity": "51.02",
                        "price": "0",
                        "realized_gain": "-18.9377",
                    },
                    {
                        "timestamp": "2026-09-30T04:31:48Z",
                        "symbol": "HOOD",
                        "side": "sell",
                        "quantity": "982",
                        "price": "119.08",
                        "realized_gain": "1330.89",
                    },
                ],
                "next_cursor": "next",
            }
        }
    )
    assert cursor == "next"
    assert len(rows) == 1
    assert rows[0]["quantity"] == "51.02"
    assert Decimal(rows[0]["realized"]) == Decimal("-18.9377")


def test_parse_open_value() -> None:
    assert parse_open_value({"data": {"event_contracts_value": "0.66"}}) == Decimal("0.66")


def test_history_label_uses_name_and_side() -> None:
    assert history_label({"description": "$78,928.65 or above", "side": "sell"}) == "$78,928.65 or above · sell"
    assert history_label({"symbol": "", "side": ""}) == ""


def test_collapse_cumulative_closes_keeps_final_fill() -> None:
    rows = [
        {"timestamp": "2026-10-29T20:52:08Z", "quantity": "7420.76", "price": "0.29", "realized": "-3581.29"},
        {"timestamp": "2026-10-29T20:52:18Z", "quantity": "8286.34", "price": "0.29", "realized": "-4040.05"},
        {"timestamp": "2026-10-29T20:52:29Z", "quantity": "8946.22", "price": "0.29", "realized": "-4389.79"},
        {"timestamp": "2026-10-29T20:52:32Z", "quantity": "9866", "price": "0.29", "realized": "-4877.27"},
        {"timestamp": "2026-10-29T19:51:49Z", "quantity": "611", "price": "1", "realized": "122.20"},
    ]
    kept = collapse_cumulative_closes(rows)
    assert [row["quantity"] for row in kept] == ["9866", "611"]


def test_collapse_keeps_separate_prices() -> None:
    rows = [
        {"timestamp": "2026-10-29T20:46:00Z", "quantity": "1449", "price": "0.65", "realized": "-970.83"},
        {"timestamp": "2026-10-29T20:48:00Z", "quantity": "2233", "price": "0.88", "realized": "-1999.17"},
    ]
    assert collapse_cumulative_closes(rows) == rows


def test_parse_open_cost() -> None:
    assert parse_open_cost({"data": {"event_contracts_cost": "12.50"}}) == Decimal("12.50")
    assert parse_open_cost({"data": {"event_contracts_value": "0.66"}}) is None
