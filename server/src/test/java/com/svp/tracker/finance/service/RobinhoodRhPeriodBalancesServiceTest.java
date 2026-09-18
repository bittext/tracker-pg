package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class RobinhoodRhPeriodBalancesServiceTest {

    @Test
    void lastBeforeUsesPriorMonthCloseAsMidnightOpen() {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 8, 31), new BigDecimal("100"));
        series.put(LocalDate.of(2026, 9, 1), new BigDecimal("110"));
        var open = RobinhoodRhPeriodBalancesService.lastBefore(series, LocalDate.of(2026, 9, 1));
        assertEquals(LocalDate.of(2026, 8, 31), open.date());
        assertEquals(0, new BigDecimal("100").compareTo(open.value()));
    }

    @Test
    void lastOnOrBeforeUsesMonthEndClose() {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 9, 29), new BigDecimal("120"));
        series.put(LocalDate.of(2026, 9, 30), new BigDecimal("125"));
        series.put(LocalDate.of(2026, 10, 1), new BigDecimal("130"));
        var close = RobinhoodRhPeriodBalancesService.lastOnOrBefore(series, LocalDate.of(2026, 9, 30));
        assertEquals(LocalDate.of(2026, 9, 30), close.date());
        assertEquals(0, new BigDecimal("125").compareTo(close.value()));
    }

    @Test
    void missingPriorCloseLeavesLastBeforeEmpty() {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 9, 2), new BigDecimal("50"));
        assertNull(RobinhoodRhPeriodBalancesService.lastBefore(series, LocalDate.of(2026, 9, 1)));
    }

    @Test
    void openingFallsBackToFirstCloseInPeriod() {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 6, 10), new BigDecimal("200000"));
        series.put(LocalDate.of(2026, 9, 4), new BigDecimal("305104.25"));
        var open = RobinhoodRhPeriodBalancesService.openingForPeriod(
                series, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 4));
        assertEquals(LocalDate.of(2026, 6, 10), open.date());
        assertEquals(0, new BigDecimal("200000").compareTo(open.value()));
    }

    @Test
    void openingPrefersPriorCloseOverFirstInPeriod() {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2025, 12, 31), new BigDecimal("180000"));
        series.put(LocalDate.of(2026, 1, 2), new BigDecimal("181000"));
        var open = RobinhoodRhPeriodBalancesService.openingForPeriod(
                series, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(LocalDate.of(2025, 12, 31), open.date());
        assertEquals(0, new BigDecimal("180000").compareTo(open.value()));
    }

    @Test
    void dailyOpeningIsPriorClose() {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 9, 10), new BigDecimal("277000"));
        series.put(LocalDate.of(2026, 9, 11), new BigDecimal("280000"));
        var open = RobinhoodRhPeriodBalancesService.openingForPeriod(
                series, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11));
        assertEquals(LocalDate.of(2026, 9, 10), open.date());
        assertEquals(0, new BigDecimal("277000").compareTo(open.value()));
    }

    @Test
    void openingIgnoresFirstCloseAfterPeriodEnd() {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 6, 2), new BigDecimal("50"));
        assertNull(RobinhoodRhPeriodBalancesService.openingForPeriod(
                series, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31)));
    }

    @Test
    void cashTotalsCountsDepositsAndWithdrawalsInTheWindow() {
        var deposit = cash("3370", LocalDate.of(2026, 9, 2), "IN", "1000");
        var withdraw = cash("3370", LocalDate.of(2026, 9, 14), "OUT", "24000");
        var prior = cash("3370", LocalDate.of(2026, 8, 30), "OUT", "50");
        var totals = RobinhoodRhPeriodBalancesService.cashTotals(
                List.of(deposit, withdraw, prior),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 18));
        assertEquals(0, new BigDecimal("1000").compareTo(totals.added()));
        assertEquals(0, new BigDecimal("24000").compareTo(totals.removed()));
    }

    @Test
    void marketChangeRemovesDepositsAndAddsWithdrawals() {
        var cash = new RobinhoodRhPeriodBalancesService.CashTotals(new BigDecimal("1000"), new BigDecimal("24000"));
        assertEquals(
                0,
                new BigDecimal("-33520.76")
                        .compareTo(RobinhoodRhPeriodBalancesService.marketChange(new BigDecimal("-56520.76"), cash)));
    }

    private static RobinhoodAccountCashIo cash(String suffix, LocalDate day, String direction, String amount) {
        var row = new RobinhoodAccountCashIo();
        row.setAccountSuffix(suffix);
        row.setActivityDate(day);
        row.setDirection(direction);
        row.setAmount(new BigDecimal(amount));
        return row;
    }
}
