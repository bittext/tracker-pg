package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NasdaqEarningsServiceTest {

    @Test
    void normalizeSymbolUppercasesAndStripsJunk() {
        assertEquals("BRK.B", NasdaqEarningsService.normalizeSymbol(" brk.b "));
        assertEquals("AAPL", NasdaqEarningsService.normalizeSymbol("AAPL!!!"));
        assertEquals("", NasdaqEarningsService.normalizeSymbol(null));
    }

    @Test
    void parseMarketCapStripsFormatting() {
        assertEquals(232_564_144_832L, NasdaqEarningsService.parseMarketCap("$232,564,144,832"));
        assertNull(NasdaqEarningsService.parseMarketCap("N/A"));
        assertNull(NasdaqEarningsService.parseMarketCap(null));
    }

    @Test
    void mapTimingRecognizesPreAndAfter() {
        assertEquals("pre-market", NasdaqEarningsService.mapTiming("time-pre-market"));
        assertEquals("after-close", NasdaqEarningsService.mapTiming("time-after-hours"));
        assertEquals("unspecified", NasdaqEarningsService.mapTiming("time-not-supplied"));
    }

    @Test
    void parseUpcomingEarningsDateFromQuoteMessage() {
        NasdaqEarningsService svc = new NasdaqEarningsService();
        assertEquals(LocalDate.of(2026, 7, 30), svc.parseUpcomingEarningsDate("Earnings Date : Jul 30, 2026"));
        assertNull(svc.parseUpcomingEarningsDate(""));
    }
}
