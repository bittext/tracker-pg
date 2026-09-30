package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RobinhoodYtdCheckServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sidecarParseKeepsBrokerYtdAndOpenMark() throws Exception {
        var root = mapper.readTree(
                """
                {
                  "as_of": "2026-09-30",
                  "account_value": "275906.60",
                  "equity_value": "475866.55",
                  "cash": "-199959.95",
                  "buying_power": "40.05",
                  "realized_ytd": "323377.61",
                  "realized_equity": "90037.49",
                  "realized_option": "233613",
                  "realized_crypto": "-272.88",
                  "realized_calendar_day": "1330.89",
                  "realized_app_day": "11876.88",
                  "app_day_trades": 5,
                  "positions": [
                    {"symbol": "MRNA", "quantity": "2323", "average_buy_price": "191.13"}
                  ],
                  "recent_closes": [
                    {"timestamp": "2026-09-30T04:31:48Z", "symbol": "HOOD", "side": "sell",
                     "quantity": "982", "price": "119.08", "realized": "1330.89"}
                  ],
                  "warnings": []
                }
                """);
        var dto = RobinhoodYtdCheckService.fromSidecar(root, 2026, LocalDate.of(2026, 9, 30), Instant.parse("2026-09-30T05:16:00Z"));
        assertEquals(0, new BigDecimal("323377.61").compareTo(dto.realizedYtd()));
        assertEquals(0, new BigDecimal("233613.00").compareTo(dto.realizedOption()));
        assertEquals(0, new BigDecimal("11876.88").compareTo(dto.realizedAppDay()));
        assertEquals(1, dto.positions().size());
        assertEquals("MRNA", dto.positions().get(0).symbol());
        assertTrue(dto.openUnrealized().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, dto.realizedYtd().add(dto.openUnrealized()).compareTo(dto.impliedYtdTotal()));
        assertEquals("HOOD", dto.recentCloses().get(0).symbol());
        assertTrue(dto.live());
    }
}
