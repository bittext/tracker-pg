package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobinhoodRhDailyTrackerPositionChangeTest {

    @Test
    void detectsQuantityChange() {
        RobinhoodRhDailySnapshot prior = snapshot(
                "3370",
                Instant.parse("2026-07-04T18:00:00Z"),
                holding("AAPL", "STOCK", "10"));
        RobinhoodRhDailySnapshot current = snapshot(
                "3370",
                Instant.parse("2026-07-04T19:00:00Z"),
                holding("AAPL", "STOCK", "12"));

        assertTrue(positionsChanged(prior, current));
    }

    @Test
    void ignoresPriceOnlyChange() {
        RobinhoodRhDailySnapshot prior = snapshot(
                "3370",
                Instant.parse("2026-07-04T18:00:00Z"),
                holding("AAPL", "STOCK", "10", "150", "160"));
        RobinhoodRhDailySnapshot current = snapshot(
                "3370",
                Instant.parse("2026-07-04T19:00:00Z"),
                holding("AAPL", "STOCK", "10", "150", "170"));

        assertFalse(positionsChanged(prior, current));
    }

    @Test
    void excludesManagedAccount4123() {
        RobinhoodRhDailySnapshot prior = snapshot(
                RobinhoodRhDailyTrackerAccountPolicy.POSITION_CHANGE_HIGHLIGHT_EXCLUDED_SUFFIX,
                Instant.parse("2026-07-04T18:00:00Z"),
                holding("VOO", "STOCK", "1"));
        RobinhoodRhDailySnapshot current = snapshot(
                RobinhoodRhDailyTrackerAccountPolicy.POSITION_CHANGE_HIGHLIGHT_EXCLUDED_SUFFIX,
                Instant.parse("2026-07-04T19:00:00Z"),
                holding("VOO", "STOCK", "5"));

        assertFalse(positionsChangedForHighlight(prior, current));
    }

    private static boolean positionsChanged(RobinhoodRhDailySnapshot prior, RobinhoodRhDailySnapshot current) {
        return !quantities(prior).equals(quantities(current));
    }

    private static boolean positionsChangedForHighlight(
            RobinhoodRhDailySnapshot prior, RobinhoodRhDailySnapshot current) {
        if (RobinhoodRhDailyTrackerAccountPolicy.POSITION_CHANGE_HIGHLIGHT_EXCLUDED_SUFFIX.equals(
                current.getAccountSuffix())) {
            return false;
        }
        return positionsChanged(prior, current);
    }

    private static RobinhoodRhHoldingDto holding(String symbol, String type, String qty) {
        return holding(symbol, type, qty, "100", "110");
    }

    private static RobinhoodRhHoldingDto holding(
            String symbol, String type, String qty, String avg, String mv) {
        return new RobinhoodRhHoldingDto(
                symbol,
                type,
                new BigDecimal(qty),
                new BigDecimal(avg),
                new BigDecimal(mv),
                new BigDecimal(mv),
                new BigDecimal(avg).multiply(new BigDecimal(qty)),
                null,
                null);
    }

    private static RobinhoodRhDailySnapshot snapshot(
            String suffix, Instant at, RobinhoodRhHoldingDto... holdings) {
        RobinhoodRhDailySnapshot row = new RobinhoodRhDailySnapshot();
        row.setOwnerUserId(2L);
        row.setAccountSuffix(suffix);
        row.setSnapshotAt(at);
        row.setSnapshotDate(LocalDate.of(2026, 7, 4));
        row.setLabel("Test");
        row.setAccountKind("BROKERAGE");
        row.setTotalAccountValue(BigDecimal.TEN);
        row.setCashBalance(BigDecimal.ZERO);
        row.setEquityMarketValue(BigDecimal.TEN);
        try {
            row.setHoldingsJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(holdings)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return row;
    }

    private static java.util.Map<String, BigDecimal> quantities(RobinhoodRhDailySnapshot row) {
        try {
            List<RobinhoodRhHoldingDto> holdings = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(row.getHoldingsJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            java.util.Map<String, BigDecimal> map = new java.util.TreeMap<>();
            for (RobinhoodRhHoldingDto h : holdings) {
                String key = h.symbol().toUpperCase() + "|" + h.positionType().toUpperCase();
                map.merge(key, h.quantity(), BigDecimal::add);
            }
            return map;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
