from decimal import Decimal

from predict_markets_service import is_event_trade, parse_event_closes, parse_open_value


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
