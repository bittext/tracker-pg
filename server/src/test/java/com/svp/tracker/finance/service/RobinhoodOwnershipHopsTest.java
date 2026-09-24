package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.dto.RobinhoodOwnershipHopDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobinhoodOwnershipHopsTest {

    @Test
    void readsNumericEpochTradesJson() {
        String json =
                """
                [{"symbol":"SKHY","side":"buy","orderType":"limit","quantity":1660.0,\
                "averagePrice":190.0217,"limitPrice":190.05,"state":"filled",\
                "executedAt":1789144987.574}]
                """;
        List<RobinhoodRhDailyTradeDto> trades = RobinhoodRhSnapshotTradeReader.read(json);
        assertEquals(1, trades.size());
        assertEquals("SKHY", trades.get(0).symbol());
        assertEquals("buy", trades.get(0).side());
        assertEquals(0, new BigDecimal("1660.0").compareTo(trades.get(0).quantity()));
        Instant at = trades.get(0).executedAt();
        assertEquals(1789144987L, at.getEpochSecond());
    }

    @Test
    void prefersTradeOverMatchingHoldingHop() {
        LocalDate friday = LocalDate.of(2026, 9, 11);
        Instant at = Instant.parse("2026-09-11T16:43:07Z");
        RobinhoodOwnershipHopDto trade = new RobinhoodOwnershipHopDto(
                at,
                friday,
                "SCHEDULED",
                "SKHY",
                "buy",
                new BigDecimal("1660.000000"),
                null,
                null,
                new BigDecimal("190.0217"),
                new BigDecimal("315436.02"),
                "trade",
                "3370",
                "Individual");
        RobinhoodOwnershipHopDto holding = new RobinhoodOwnershipHopDto(
                Instant.parse("2026-09-11T17:00:00Z"),
                friday,
                "INTRADAY",
                "SKHY",
                "buy",
                new BigDecimal("1660.000000"),
                BigDecimal.ZERO,
                new BigDecimal("1660.000000"),
                null,
                null,
                "holding",
                "3370",
                "Individual");
        List<RobinhoodOwnershipHopDto> merged = RobinhoodOwnershipHistoryService.mergeHops(List.of(trade), List.of(holding));
        assertEquals(1, merged.size());
        assertEquals("trade", merged.get(0).source());
        assertTrue(RobinhoodOwnershipHistoryService.coveredByTrade(List.of(trade), holding));
    }

    @Test
    void countsOnlySharesThatFilled() {
        LocalDate day = LocalDate.of(2026, 9, 11);
        List<RobinhoodOwnershipHopDto> full = List.of(
                hop(day, "buy", "1950"),
                hop(day, "buy", "130"),
                hop(day, "buy", "14"),
                hop(day, "buy", "866"));
        List<RobinhoodOwnershipHopDto> partial = List.of(hop(day, "buy", "2138"));
        Map<String, Map<LocalDate, BigDecimal>> endQty = new LinkedHashMap<>();
        endQty.put("MRNA", Map.of(day, new BigDecimal("3000")));

        List<RobinhoodOwnershipHopDto> fills =
                RobinhoodOwnershipHistoryService.filledTrades(full, partial, endQty);

        BigDecimal net = BigDecimal.ZERO;
        for (RobinhoodOwnershipHopDto h : fills) {
            net = net.add(h.quantity());
            assertTrue(h.quantity().compareTo(new BigDecimal("2138")) < 0);
        }
        assertEquals(0, net.compareTo(new BigDecimal("3000")));
        assertEquals(5, fills.size());
        assertEquals(0, fills.get(4).quantity().compareTo(new BigDecimal("40.000000")));
    }

    private static RobinhoodOwnershipHopDto hop(LocalDate day, String side, String qty) {
        return new RobinhoodOwnershipHopDto(
                Instant.parse("2026-09-11T17:00:00Z"),
                day,
                "SCHEDULED",
                "MRNA",
                side,
                new BigDecimal(qty),
                null,
                null,
                new BigDecimal("147.25"),
                null,
                "trade",
                "3370",
                "Individual");
    }
}
