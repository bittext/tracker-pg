package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.svp.tracker.finance.domain.MarketsJourneyEntry;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketsJourneyLiveNetTest {

    @Test
    void dailyTotalsIncludesAmmuAndSumsAllAccounts() {
        LocalDate day = LocalDate.of(2026, 8, 21);
        List<MarketsJourneyLiveNet.DayTotal> daily = MarketsJourneyLiveNet.dailyTotals(List.of(
                row(day, "3370", "367374.86"),
                row(day, "3550", "1616.61"),
                row(day, "4123", "1304.87"),
                row(day, "2835", "604.18"),
                row(day, "8696", "50000.00")));

        assertEquals(1, daily.size());
        assertEquals(0, new BigDecimal("420900.52").compareTo(daily.get(0).total()));
        assertEquals(5, daily.get(0).accounts().size());
        assertTrue(daily.get(0).accounts().stream().anyMatch(a -> "8696".equals(a.suffix())));
    }

    @Test
    void dailyTotalsForwardFillsMissingAccountsAndTracksDayChange() {
        LocalDate first = LocalDate.of(2026, 8, 20);
        LocalDate second = LocalDate.of(2026, 8, 21);
        List<MarketsJourneyLiveNet.DayTotal> daily = MarketsJourneyLiveNet.dailyTotals(List.of(
                row(first, "3370", "360000"),
                row(first, "3550", "1600"),
                row(second, "3370", "367000")));

        assertEquals(2, daily.size());
        assertEquals(0, new BigDecimal("361600.00").compareTo(daily.get(0).total()));
        assertEquals(0, new BigDecimal("368600.00").compareTo(daily.get(1).total()));
        assertEquals(0, new BigDecimal("7000.00").compareTo(MarketsJourneyLiveNet.dayChange(daily.get(0), daily.get(1))));
        assertEquals(
                0, new BigDecimal("1.94").compareTo(MarketsJourneyLiveNet.dayChangePct(daily.get(0), daily.get(1))));
    }

    @Test
    void seriesKeepsRecentDailyCloses() {
        List<RhScheduledTotalRow> rows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 2);
        for (int i = 0; i < 40; i++) {
            rows.add(row(start.plusDays(i * 7), "3370", String.valueOf(300_000 + i)));
        }
        List<MarketsJourneyLiveNet.DayTotal> series =
                MarketsJourneyLiveNet.seriesForChart(MarketsJourneyLiveNet.dailyTotals(rows));
        assertEquals(rows.get(rows.size() - 1).snapshotDate(), series.get(series.size() - 1).date());
        assertTrue(series.size() >= 2);
    }

    @Test
    void autoManagedRecognizesSeededNotesOnly() {
        MarketsJourneyEntry auto = new MarketsJourneyEntry();
        auto.setActualNote(MarketsJourneyLiveNet.actualNote(LocalDate.of(2026, 8, 21)));
        assertTrue(MarketsJourneyLiveNet.isAutoManaged(auto));

        MarketsJourneyEntry manual = new MarketsJourneyEntry();
        manual.setActualAmount(new BigDecimal("100"));
        manual.setActualNote("Hand-entered Q3 close");
        assertFalse(MarketsJourneyLiveNet.isAutoManaged(manual));
    }

    private static RhScheduledTotalRow row(LocalDate date, String suffix, String value) {
        return new RhScheduledTotalRow(date, suffix, new BigDecimal(value));
    }
}
