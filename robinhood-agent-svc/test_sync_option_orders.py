from sync_service import _format_option_symbol, _normalize_order, _option_side


def test_option_order_uses_leg_for_symbol_side_and_premium():
    row = {
        "id": "6aaad535-51f2-4f9b-b0b1-fe5f7dab6c2d",
        "chain_symbol": "MRNA",
        "state": "filled",
        "type": "limit",
        "quantity": "1.00000",
        "processed_quantity": "1.00000",
        "price": "4.45000000",
        "premium": "445.00000000",
        "processed_premium": "445",
        "created_at": "2026-09-16T17:43:17.944015Z",
        "updated_at": "2026-09-16T17:43:18.414024Z",
        "legs": [
            {
                "side": "buy",
                "position_effect": "open",
                "expiration_date": "2026-09-25",
                "strike_price": "155.0000",
                "option_type": "call",
                "executions": [{"price": "4.45000000", "quantity": "1.00000"}],
            }
        ],
    }
    assert _format_option_symbol(row) == "MRNA $155 Call 2026-09-25"
    assert _option_side(row) == "buy"
    normalized = _normalize_order(row, option=True)
    assert normalized["symbol"] == "MRNA $155 Call 2026-09-25"
    assert normalized["side"] == "buy"
    assert normalized["average_price"] == "4.45000000"
    assert normalized["quantity"] == "1.00000"


def test_option_sell_to_close_formats_hood_contract():
    row = {
        "id": "hood-1",
        "chain_symbol": "HOOD",
        "type": "limit",
        "processed_quantity": "1.00000",
        "price": "4.10000000",
        "processed_premium": "410",
        "legs": [
            {
                "side": "sell",
                "position_effect": "close",
                "expiration_date": "2027-01-15",
                "strike_price": "155.0000",
                "option_type": "call",
                "executions": [{"price": "4.10000000"}],
            }
        ],
    }
    normalized = _normalize_order(row, option=True)
    assert normalized["symbol"] == "HOOD $155 Call 2027-01-15"
    assert normalized["side"] == "sell"


def test_five_contract_mrna_buy_keeps_processed_quantity():
    row = {
        "id": "ind-1",
        "chain_symbol": "MRNA",
        "type": "limit",
        "quantity": "5.00000",
        "processed_quantity": "5.00000",
        "price": "7.50000000",
        "processed_premium": "3750",
        "legs": [
            {
                "side": "buy",
                "position_effect": "open",
                "expiration_date": "2026-09-25",
                "strike_price": "148.0000",
                "option_type": "call",
            }
        ],
    }
    normalized = _normalize_order(row, option=True)
    assert normalized["symbol"] == "MRNA $148 Call 2026-09-25"
    assert normalized["quantity"] == "5.00000"
    assert normalized["average_price"] == "7.50000000"
