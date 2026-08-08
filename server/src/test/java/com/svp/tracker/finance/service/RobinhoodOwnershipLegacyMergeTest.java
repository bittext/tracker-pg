package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RobinhoodOwnershipLegacyMergeTest {

    @Test
    void detectsLegacyAndEnrichedKeys() {
        assertTrue(RobinhoodOwnershipHistoryService.isLegacyContractKey("LEGACY|AAPL|2.48"));
        assertFalse(RobinhoodOwnershipHistoryService.isLegacyContractKey("AAPL|call|150|2026-07-18"));
        assertTrue(RobinhoodOwnershipHistoryService.isEnrichedContractKey("AAPL|call|150|2026-07-18"));
        assertFalse(RobinhoodOwnershipHistoryService.isEnrichedContractKey("LEGACY|AAPL|2.48"));
        assertEquals("AAPL", RobinhoodOwnershipHistoryService.chainFromContractKey("LEGACY|AAPL|2.48", null));
        assertEquals(
                "AAPL", RobinhoodOwnershipHistoryService.chainFromContractKey("AAPL|call|150|2026-07-18", null));
    }

    @Test
    void scoresContinuousSameQtyAndAvgHighly() {
        double score = RobinhoodOwnershipHistoryService.legacyMergeScore(
                LocalDate.of(2026, 8, 1),
                new BigDecimal("39"),
                new BigDecimal("2.48"),
                LocalDate.of(2026, 8, 2),
                new BigDecimal("39"),
                new BigDecimal("2.4808"));
        assertTrue(score >= 8.0, "expected mergeable score, got " + score);
    }

    @Test
    void rejectsDistantDatesOrMismatchedQty() {
        assertTrue(
                RobinhoodOwnershipHistoryService.legacyMergeScore(
                                LocalDate.of(2026, 6, 1),
                                new BigDecimal("39"),
                                new BigDecimal("2.48"),
                                LocalDate.of(2026, 8, 2),
                                new BigDecimal("39"),
                                new BigDecimal("2.48"))
                        < 0);
        assertTrue(
                RobinhoodOwnershipHistoryService.legacyMergeScore(
                                LocalDate.of(2026, 8, 1),
                                new BigDecimal("39"),
                                new BigDecimal("2.48"),
                                LocalDate.of(2026, 8, 2),
                                new BigDecimal("2"),
                                new BigDecimal("2.48"))
                        < 0);
    }
}
