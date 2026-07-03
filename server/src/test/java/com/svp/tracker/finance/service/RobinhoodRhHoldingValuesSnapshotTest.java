package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhLiveQuotesDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobinhoodRhHoldingValuesSnapshotTest {

    @Test
    void legacyOptionUsesMarkWhenMarketValueEqualsCost() {
        RobinhoodRhHoldingDto raw = new RobinhoodRhHoldingDto(
                "NBIS",
                "option",
                new BigDecimal("1"),
                new BigDecimal("1000"),
                new BigDecimal("1068"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        RobinhoodRhHoldingDto fixed =
                RobinhoodRhHoldingValues.normalizeStoredSnapshotHoldings(List.of(raw)).get(0);

        assertEquals(new BigDecimal("10.0000"), fixed.averageBuyPrice());
        assertEquals(new BigDecimal("10.6800"), fixed.currentUnitPrice());
    }

    @Test
    void legacyOptionUsesUnrealizedWhenMarketValueEqualsCost() {
        RobinhoodRhHoldingDto raw = new RobinhoodRhHoldingDto(
                "NBIS",
                "option",
                new BigDecimal("1"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                new BigDecimal("68.00"),
                BigDecimal.ZERO);

        RobinhoodRhHoldingDto fixed =
                RobinhoodRhHoldingValues.normalizeStoredSnapshotHoldings(List.of(raw)).get(0);

        assertEquals(new BigDecimal("10.6800"), fixed.currentUnitPrice());
        assertEquals(new BigDecimal("1068.00"), fixed.marketValue());
    }

    @Test
    void legacyOptionWithoutPositionTypeStillNormalizes() {
        RobinhoodRhHoldingDto raw = new RobinhoodRhHoldingDto(
                "NBIS",
                null,
                new BigDecimal("1"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        RobinhoodRhHoldingDto fixed =
                RobinhoodRhHoldingValues.normalizeStoredSnapshotHoldings(List.of(raw)).get(0);

        assertEquals("option", fixed.positionType());
        assertEquals(new BigDecimal("10.0000"), fixed.averageBuyPrice());
        assertEquals(new BigDecimal("10.0000"), fixed.currentUnitPrice());
    }

    @Test
    void inflatedMarketValueAndUnitPriceCollapseToPerShare() {
        RobinhoodRhHoldingDto raw = new RobinhoodRhHoldingDto(
                "NBIS",
                "option",
                new BigDecimal("1"),
                new BigDecimal("1000"),
                new BigDecimal("100000"),
                new BigDecimal("100000"),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        RobinhoodRhHoldingDto fixed =
                RobinhoodRhHoldingValues.normalizeStoredSnapshotHoldings(List.of(raw)).get(0);

        assertEquals(new BigDecimal("10.0000"), fixed.currentUnitPrice());
        assertEquals(new BigDecimal("1000.00"), fixed.marketValue());
    }

    @Test
    void optionMarketValueRoundsMarkBeforeMultiply() {
        RobinhoodRhHoldingDto raw = new RobinhoodRhHoldingDto(
                "NBIS",
                "option",
                new BigDecimal("1"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        RobinhoodRhLiveQuotesDto quotes = new RobinhoodRhLiveQuotesDto(
                Map.of(),
                Map.of("inst-1", new BigDecimal("10.675")));
        Map<String, String> optionIds = Map.of("NBIS|1|10", "inst-1");

        RobinhoodRhHoldingDto fixed = RobinhoodRhHoldingValues.finalizeHoldings(
                        List.of(raw), BigDecimal.ZERO, quotes, optionIds)
                .get(0);

        assertEquals(new BigDecimal("1068.00"), fixed.marketValue());
        assertEquals(new BigDecimal("10.6800"), fixed.currentUnitPrice());
    }
}
