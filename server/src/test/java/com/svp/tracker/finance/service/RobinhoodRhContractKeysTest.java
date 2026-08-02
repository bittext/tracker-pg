package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RobinhoodRhContractKeysTest {

    @Test
    void enrichedOptionUsesChainTypeStrikeExpiry() {
        RobinhoodRhHoldingDto h = new RobinhoodRhHoldingDto(
                "NBIS",
                "option",
                new BigDecimal("2"),
                new BigDecimal("10"),
                new BigDecimal("11"),
                new BigDecimal("2200"),
                new BigDecimal("2000"),
                new BigDecimal("200"),
                new BigDecimal("10"),
                "inst|leg|call",
                "NBIS",
                "call",
                new BigDecimal("50"),
                LocalDate.of(2026, 7, 18));

        assertEquals("NBIS|call|50|2026-07-18", RobinhoodRhContractKeys.contractKeyForHolding(h));
        assertEquals("NBIS CALL 50 · 2026-07-18", RobinhoodRhContractKeys.contractLabel(h));
    }

    @Test
    void legacyOptionUsesChainAndAverage() {
        RobinhoodRhHoldingDto h = new RobinhoodRhHoldingDto(
                "AAPL",
                "option",
                new BigDecimal("39"),
                new BigDecimal("2.4808"),
                new BigDecimal("2.65"),
                new BigDecimal("10335"),
                new BigDecimal("9675"),
                new BigDecimal("660"),
                new BigDecimal("6.8"));

        assertEquals("LEGACY|AAPL|2.48", RobinhoodRhContractKeys.contractKeyForHolding(h));
        assertTrue(RobinhoodRhContractKeys.isLegacyIdentity(h));
        assertTrue(RobinhoodRhContractKeys.contractLabel(h).contains("AAPL option @ $2.48"));
    }
}
