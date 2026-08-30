"""Parse helpers for Robinhood MCP get_financials."""

from financials_service import _rows_from_payload, normalize_financial_row


def test_normalize_hood_q2_row() -> None:
    row = normalize_financial_row(
        {
            "fiscal_year": 2026,
            "fiscal_quarter": 2,
            "period_end_date": "2026-06-30",
            "revenue": "1308000000.000000",
            "gross_profit": None,
            "net_income": "561000000.000000",
            "net_margin": "42.890000",
        }
    )
    assert row is not None
    assert row["period_end_date"] == "2026-06-30"
    assert row["revenue"] == "1308000000.000000"
    assert row["net_margin"] == "42.890000"


def test_rows_from_mcp_envelope() -> None:
    payload = {
        "data": {
            "results": [
                {
                    "symbol": "HOOD",
                    "period": "quarterly",
                    "financials": [
                        {
                            "fiscal_year": 2026,
                            "fiscal_quarter": 2,
                            "period_end_date": "2026-06-30",
                            "revenue": "1308000000.000000",
                            "gross_profit": None,
                            "net_income": "561000000.000000",
                            "net_margin": "42.890000",
                        }
                    ],
                }
            ]
        },
        "guide": "ignored",
    }
    rows = _rows_from_payload(payload, "hood")
    assert len(rows) == 1
    assert rows[0]["revenue"] == "1308000000.000000"
