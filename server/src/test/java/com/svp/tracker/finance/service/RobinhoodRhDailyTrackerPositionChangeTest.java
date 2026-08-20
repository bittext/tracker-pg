package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodRhDailySnapshotHoldingDto;
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

        assertTrue(RobinhoodRhDailySnapshotCompare.positionsChanged(prior, current));
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

        assertFalse(RobinhoodRhDailySnapshotCompare.positionsChanged(prior, current));
    }

    @Test
    void holdingsDeltaShowsQuantityAndValueChanges() {
        List<RobinhoodRhDailySnapshotHoldingDto> rows = RobinhoodRhDailySnapshotCompare.holdingsWithPriorDeltas(
                List.of(holding("AAPL", "STOCK", "12", "150", "2040")),
                List.of(holding("AAPL", "STOCK", "10", "150", "1600")));

        assertEquals(1, rows.size());
        RobinhoodRhDailySnapshotHoldingDto row = rows.get(0);
        assertFalse(row.exited());
        assertEquals(new BigDecimal("2.0000"), row.quantityChange());
        assertEquals(new BigDecimal("440.00"), row.marketValueChange());
    }

    @Test
    void holdingsDeltaOmitsPriceChangeWhenPositionIsNew() {
        List<RobinhoodRhDailySnapshotHoldingDto> rows = RobinhoodRhDailySnapshotCompare.holdingsWithPriorDeltas(
                List.of(holding("AAPL", "STOCK", "10", "150", "1600")),
                List.of());

        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("10.0000"), rows.get(0).quantityChange());
        assertEquals(new BigDecimal("1600.00"), rows.get(0).marketValueChange());
        assertEquals(null, rows.get(0).currentUnitPriceChange());
    }

    @Test
    void holdingsDeltaAppendsExitedPositions() {
        List<RobinhoodRhDailySnapshotHoldingDto> rows = RobinhoodRhDailySnapshotCompare.holdingsWithPriorDeltas(
                List.of(),
                List.of(holding("AAPL", "STOCK", "10", "150", "1600")));

        assertEquals(1, rows.size());
        RobinhoodRhDailySnapshotHoldingDto row = rows.get(0);
        assertTrue(row.exited());
        assertEquals("AAPL", row.holding().symbol());
        assertEquals(new BigDecimal("-10.0000"), row.quantityChange());
        assertEquals(new BigDecimal("-1600.00"), row.marketValueChange());
    }

    @Test
    void holdingsDeltaShowsUnitPriceChangeWhenPositionExists() {
        RobinhoodRhHoldingDto prior = new RobinhoodRhHoldingDto(
                "AAPL",
                "STOCK",
                new BigDecimal("10"),
                new BigDecimal("150"),
                new BigDecimal("160"),
                new BigDecimal("1600"),
                new BigDecimal("1500"),
                new BigDecimal("100"),
                null);
        RobinhoodRhHoldingDto current = new RobinhoodRhHoldingDto(
                "AAPL",
                "STOCK",
                new BigDecimal("10"),
                new BigDecimal("150"),
                new BigDecimal("170"),
                new BigDecimal("1700"),
                new BigDecimal("1500"),
                new BigDecimal("200"),
                null);

        RobinhoodRhDailySnapshotHoldingDto row =
                RobinhoodRhDailySnapshotCompare.holdingsWithPriorDeltas(List.of(current), List.of(prior)).get(0);
        assertEquals(null, row.quantityChange());
        assertEquals(new BigDecimal("10.0000"), row.currentUnitPriceChange());
        assertEquals(new BigDecimal("100.00"), row.marketValueChange());
    }

    @Test
    void holdingsDeltaSkipsUnchangedFieldsAndLeavesFirstSnapshotBlank() {
        List<RobinhoodRhDailySnapshotHoldingDto> unchanged = RobinhoodRhDailySnapshotCompare.holdingsWithPriorDeltas(
                List.of(holding("AAPL", "STOCK", "10", "150", "1600")),
                List.of(holding("AAPL", "STOCK", "10", "150", "1600")));
        assertEquals(null, unchanged.get(0).quantityChange());
        assertEquals(null, unchanged.get(0).marketValueChange());

        List<RobinhoodRhDailySnapshotHoldingDto> first = RobinhoodRhDailySnapshotCompare.holdingsWithPriorDeltas(
                List.of(holding("AAPL", "STOCK", "10", "150", "1600")),
                null);
        assertEquals(null, first.get(0).quantityChange());
        assertEquals(null, first.get(0).marketValueChange());
    }

    @Test
    void excludesManagedAccount4123FromHighlightPolicy() {
        RobinhoodRhDailySnapshot prior = snapshot(
                RobinhoodRhDailyTrackerAccountPolicy.POSITION_CHANGE_HIGHLIGHT_EXCLUDED_SUFFIX,
                Instant.parse("2026-07-04T18:00:00Z"),
                holding("VOO", "STOCK", "1"));
        RobinhoodRhDailySnapshot current = snapshot(
                RobinhoodRhDailyTrackerAccountPolicy.POSITION_CHANGE_HIGHLIGHT_EXCLUDED_SUFFIX,
                Instant.parse("2026-07-04T19:00:00Z"),
                holding("VOO", "STOCK", "5"));

        assertTrue(RobinhoodRhDailySnapshotCompare.positionsChanged(prior, current));
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
}
