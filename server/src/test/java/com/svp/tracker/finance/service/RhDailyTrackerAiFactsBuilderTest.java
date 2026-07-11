package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerDayDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RhDailyTrackerAiFactsBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void buildsBuySellAndTopSymbols() throws Exception {
        RobinhoodRhDailyTrackerDayDto day = day(
                LocalDate.of(2026, 7, 10),
                "10000",
                "150",
                List.of(
                        trade("AAPL", "buy", "10", "150", Instant.parse("2026-07-10T15:00:00Z")),
                        trade("AAPL", "buy", "5", "151", Instant.parse("2026-07-10T16:00:00Z")),
                        trade("TSLA", "sell", "2", "250", Instant.parse("2026-07-10T17:00:00Z"))));

        var bundle = RhDailyTrackerAiFactsBuilder.build(mapper, "DAY", "2026-07-10", "Jul 10", List.of(day));
        assertEquals(3, bundle.digest().tradeCount());
        assertEquals(2, bundle.digest().buyCount());
        assertEquals(1, bundle.digest().sellCount());
        assertEquals(2, bundle.digest().uniqueSymbols());
        assertEquals(List.of("AAPL", "TSLA"), bundle.digest().topSymbolsByCount());
        assertNotNull(bundle.factsHash());
        assertFalse(bundle.factsHash().isBlank());

        JsonNode root = mapper.readTree(bundle.factsJson());
        assertTrue(root.path("disclaimer").asText().contains("no realized P&L"));
        assertEquals(3, root.path("tradeCount").asInt());
    }

    @Test
    void hashesStableForSameFacts() throws Exception {
        RobinhoodRhDailyTrackerDayDto day = day(
                LocalDate.of(2026, 7, 1),
                "9000",
                "0",
                List.of(trade("MSFT", "buy", "1", "400", Instant.parse("2026-07-01T14:00:00Z"))));
        var a = RhDailyTrackerAiFactsBuilder.build(mapper, "MONTH", "2026-07", "July 2026", List.of(day));
        var b = RhDailyTrackerAiFactsBuilder.build(mapper, "MONTH", "2026-07", "July 2026", List.of(day));
        assertEquals(a.factsHash(), b.factsHash());
        assertEquals(a.factsJson(), b.factsJson());
    }

    private static RobinhoodRhDailyTrackerDayDto day(
            LocalDate date, String total, String change, List<RobinhoodRhDailyTradeDto> trades) {
        return new RobinhoodRhDailyTrackerDayDto(
                date,
                Instant.parse(date + "T21:00:00Z"),
                true,
                new BigDecimal(total),
                new BigDecimal(change),
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                trades,
                "");
    }

    private static RobinhoodRhDailyTradeDto trade(
            String symbol, String side, String qty, String price, Instant at) {
        return new RobinhoodRhDailyTradeDto(
                symbol,
                side,
                "market",
                new BigDecimal(qty),
                new BigDecimal(price),
                null,
                "filled",
                at,
                "1234",
                "Test");
    }
}
