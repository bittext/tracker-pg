from decimal import Decimal

from ytd_check_service import parse_portfolio, parse_positions, parse_recent_closes


def test_parse_portfolio_reads_buying_power_object() -> None:
    parsed = parse_portfolio(
        {
            "data": {
                "total_value": "275906.60",
                "equity_value": "475866.55",
                "cash": "-199959.95",
                "buying_power": {"buying_power": "40.05"},
            }
        }
    )
    assert parsed["account_value"] == "275906.60"
    assert parsed["cash"] == "-199959.95"
    assert parsed["buying_power"] == "40.05"


def test_parse_positions_keeps_symbol_and_cost() -> None:
    rows = parse_positions(
        {"data": {"positions": [{"symbol": "mrna", "quantity": "2323", "average_buy_price": "191.13"}]}}
    )
    assert rows == [{"symbol": "MRNA", "quantity": "2323", "average_buy_price": "191.13"}]


def test_parse_recent_closes_limits_and_maps_gain() -> None:
    rows = parse_recent_closes(
        {
            "data": {
                "trades": [
                    {
                        "timestamp": "2026-09-30T04:31:48Z",
                        "symbol": "HOOD",
                        "side": "sell",
                        "quantity": "982",
                        "price": "119.08",
                        "realized_gain": "1330.89",
                    }
                ]
            }
        }
    )
    assert rows[0]["symbol"] == "HOOD"
    assert Decimal(rows[0]["realized"]) == Decimal("1330.89")
