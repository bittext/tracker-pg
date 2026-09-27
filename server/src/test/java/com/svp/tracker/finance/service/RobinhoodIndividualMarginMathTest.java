package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.domain.RhIndividualMarginPeek;
import com.svp.tracker.finance.dto.RobinhoodIndividualMarginPeekDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobinhoodIndividualMarginMathTest {

    @Test
    void debitIsTheAbsoluteNegativeCash() {
        assertEquals(new BigDecimal("269165.71"), RobinhoodIndividualMarginMath.marginDebit(new BigDecimal("-269165.71")));
        assertEquals(new BigDecimal("0.00"), RobinhoodIndividualMarginMath.marginDebit(new BigDecimal("98.86")));
    }

    @Test
    void borrowPercentIsDebitOverEquityBook() {
        assertEquals(
                new BigDecimal("54.43"),
                RobinhoodIndividualMarginMath.borrowPercent(new BigDecimal("269165.71"), new BigDecimal("494545.75")));
        assertTrue(RobinhoodIndividualMarginMath.highBorrow(new BigDecimal("54.43")));
        assertFalse(RobinhoodIndividualMarginMath.highBorrow(new BigDecimal("49.99")));
    }

    @Test
    void bufferWithinFivePercentIsNearCall() {
        BigDecimal maint = RobinhoodIndividualMarginMath.estimatedMaintenance(new BigDecimal("494545.75"));
        assertEquals(new BigDecimal("163811.45"), maint);

        BigDecimal healthyBuffer = RobinhoodIndividualMarginMath.bufferAmount(new BigDecimal("265555.03"), maint);
        BigDecimal healthyPct = RobinhoodIndividualMarginMath.bufferPercent(healthyBuffer, new BigDecimal("265555.03"));
        assertEquals("LOW", RobinhoodIndividualMarginMath.riskStatus(healthyPct));
        assertFalse(RobinhoodIndividualMarginMath.nearCall(healthyPct));

        assertTrue(RobinhoodIndividualMarginMath.nearCall(new BigDecimal("5.00")));
        assertTrue(RobinhoodIndividualMarginMath.nearCall(new BigDecimal("4.99")));
        assertFalse(RobinhoodIndividualMarginMath.nearCall(new BigDecimal("5.01")));
        assertEquals("NEAR_CALL", RobinhoodIndividualMarginMath.riskStatus(new BigDecimal("5.00")));
        assertEquals("CALL", RobinhoodIndividualMarginMath.riskStatus(new BigDecimal("0.00")));
    }

    @Test
    void applyComputedMarksDeepRedBorrowAndLowRiskBuffer() {
        RhIndividualMarginPeek peek = new RhIndividualMarginPeek();
        RobinhoodIndividualMarginWatchService.applyComputed(
                peek,
                1L,
                Instant.parse("2026-09-27T16:00:00Z"),
                LocalDate.of(2026, 9, 27),
                "MANUAL",
                new BigDecimal("-269165.71"),
                new BigDecimal("494545.75"),
                new BigDecimal("265555.03"),
                new BigDecimal("26075.00"),
                new BigDecimal("98.86"),
                new BigDecimal("49.43"));

        assertEquals(new BigDecimal("269165.71"), peek.getMarginDebit());
        assertEquals(new BigDecimal("54.43"), peek.getBorrowPercent());
        assertTrue(peek.isHighBorrow());
        assertFalse(peek.isNearCall());
        assertEquals("LOW", peek.getRiskStatus());
        assertEquals(new BigDecimal("98.86"), peek.getBuyingPower());
    }

    @Test
    void toneIsDeepWhenBorrowIsHalfTheBook() {
        RobinhoodIndividualMarginPeekDto latest = new RobinhoodIndividualMarginPeekDto(
                1L,
                Instant.parse("2026-09-27T16:00:00Z"),
                LocalDate.of(2026, 9, 27),
                "MANUAL",
                new BigDecimal("-269165.71"),
                new BigDecimal("494545.75"),
                new BigDecimal("265555.03"),
                null,
                new BigDecimal("98.86"),
                null,
                new BigDecimal("269165.71"),
                new BigDecimal("54.43"),
                new BigDecimal("4.75"),
                new BigDecimal("35.01"),
                new BigDecimal("163811.45"),
                "ESTIMATED",
                new BigDecimal("101743.58"),
                new BigDecimal("38.31"),
                false,
                true,
                "LOW",
                "Low risk",
                null,
                null);
        assertEquals("deep", RobinhoodIndividualMarginWatchService.toneFor(latest));
    }

    @Test
    void ledgerKeepsTheFirstPeekAndOnlyLaterMoves() {
        RhIndividualMarginPeek first = peekAt("2026-09-25T02:00:00Z", new BigDecimal("-254813.40"));
        RhIndividualMarginPeek same = peekAt("2026-09-26T02:00:00Z", new BigDecimal("-254813.40"));
        RhIndividualMarginPeek moved = peekAt("2026-09-27T16:00:00Z", new BigDecimal("-269165.71"));

        var printed = RobinhoodIndividualMarginWatchService.ledgerChangePeeks(List.of(first, same, moved));
        assertEquals(2, printed.size());
        assertEquals(first.getCapturedAt(), printed.get(0).getCapturedAt());
        assertEquals(moved.getCapturedAt(), printed.get(1).getCapturedAt());
        assertFalse(RobinhoodIndividualMarginWatchService.monitoredChanged(first, same));
        assertTrue(RobinhoodIndividualMarginWatchService.monitoredChanged(first, moved));
    }

    private static RhIndividualMarginPeek peekAt(String capturedAt, BigDecimal cash) {
        RhIndividualMarginPeek peek = new RhIndividualMarginPeek();
        RobinhoodIndividualMarginWatchService.applyComputed(
                peek,
                1L,
                Instant.parse(capturedAt),
                LocalDate.parse(capturedAt.substring(0, 10)),
                "INTRADAY",
                cash,
                new BigDecimal("494545.75"),
                new BigDecimal("265555.03"),
                null,
                new BigDecimal("98.86"),
                null);
        return peek;
    }
}
