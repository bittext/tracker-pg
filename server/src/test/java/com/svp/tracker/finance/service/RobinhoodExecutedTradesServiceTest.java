package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RobinhoodExecutedTradesServiceTest {

    @Test
    void filledAndPartialCountAsExecuted() {
        assertTrue(RobinhoodExecutedTradesService.isExecutedTrade("filled"));
        assertTrue(RobinhoodExecutedTradesService.isExecutedTrade("partially_filled"));
        assertTrue(RobinhoodExecutedTradesService.isExecutedTrade("Filled"));
    }

    @Test
    void openAndCancelledAreNotExecuted() {
        assertFalse(RobinhoodExecutedTradesService.isExecutedTrade("queued"));
        assertFalse(RobinhoodExecutedTradesService.isExecutedTrade("cancelled"));
        assertFalse(RobinhoodExecutedTradesService.isExecutedTrade("rejected"));
        assertFalse(RobinhoodExecutedTradesService.isExecutedTrade("filled_cancelled"));
        assertFalse(RobinhoodExecutedTradesService.isExecutedTrade(null));
    }

    @Test
    void lastFourUsesDigitsOnly() {
        assertEquals("3550", RobinhoodExecutedTradesService.lastFour("799863550"));
        assertEquals("3370", RobinhoodExecutedTradesService.lastFour("•••3370"));
    }

    @Test
    void underlyingSymbolUsesFirstToken() {
        assertEquals("HOOD", RobinhoodExecutedTradesService.underlyingSymbol("HOOD $155 CALL 2027-01-15"));
        assertEquals("MU", RobinhoodExecutedTradesService.underlyingSymbol("MU"));
        assertEquals("MRVL", RobinhoodExecutedTradesService.underlyingSymbol(" mrvl "));
    }

    @Test
    void notionalIsQtyTimesPriceWithOptionMultiplier() {
        assertEquals(
                0,
                new BigDecimal("11.05")
                        .compareTo(RobinhoodExecutedTradesService.notional(
                                "MU", new BigDecimal("1"), new BigDecimal("11.05"))));
        assertEquals(
                0,
                new BigDecimal("1105.00")
                        .compareTo(RobinhoodExecutedTradesService.notional(
                                "HOOD $155 CALL 2027-01-15", new BigDecimal("1"), new BigDecimal("11.05"))));
        assertEquals(
                0,
                new BigDecimal("108.54")
                        .compareTo(RobinhoodExecutedTradesService.notional(
                                "MRVL", new BigDecimal("0.493575"), new BigDecimal("219.9101"))));
    }

    @Test
    void consumeSellComputesFifoGainAndPercent() {
        var lots = new java.util.ArrayDeque<RobinhoodExecutedTradesService.Lot>();
        RobinhoodExecutedTradesService.addLot(
                lots, "HOOD", new BigDecimal("10"), new BigDecimal("10.00"));
        var realized = RobinhoodExecutedTradesService.consumeSell(
                lots, "HOOD", new BigDecimal("10"), new BigDecimal("12.00"));
        assertEquals(0, new BigDecimal("20.00").compareTo(realized.pnl()));
        assertEquals(0, new BigDecimal("20.0").compareTo(realized.percent()));
    }

    @Test
    void consumeSellWithoutBuysLeavesPnlEmpty() {
        var lots = new java.util.ArrayDeque<RobinhoodExecutedTradesService.Lot>();
        var realized = RobinhoodExecutedTradesService.consumeSell(
                lots, "HOOD", new BigDecimal("2"), new BigDecimal("12.00"));
        assertEquals(null, realized.pnl());
        assertEquals(null, realized.percent());
    }

    @Test
    void consumeSellUsesOptionMultiplier() {
        var lots = new java.util.ArrayDeque<RobinhoodExecutedTradesService.Lot>();
        RobinhoodExecutedTradesService.addLot(
                lots, "HOOD $155 CALL 2027-01-15", new BigDecimal("1"), new BigDecimal("11.05"));
        var realized = RobinhoodExecutedTradesService.consumeSell(
                lots, "HOOD $155 CALL 2027-01-15", new BigDecimal("1"), new BigDecimal("15.05"));
        assertEquals(0, new BigDecimal("400.00").compareTo(realized.pnl()));
        assertEquals(0, new BigDecimal("36.2").compareTo(realized.percent()));
    }
}
