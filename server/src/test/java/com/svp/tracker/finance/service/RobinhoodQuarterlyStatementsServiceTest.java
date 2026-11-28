package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.svp.tracker.finance.dto.RobinhoodStatementDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobinhoodQuarterlyStatementsServiceTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static RobinhoodStatementDto row(
            String suffix, String open, String close, String dep, String wd, String tin, String tout, String realized) {
        return new RobinhoodStatementDto(
                suffix, suffix, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 9, 30), d(open), d(close), d(dep), d(wd),
                d(tin), d(tout), realized == null ? null : d(realized), d("0"), null, null, null, d("0"), d("0"), null);
    }

    @Test
    void combinedCancelsMovesBetweenOwnAccountsAndReconciles() {
        var a = row("3370", "1000", "1700", "0", "0", "0", "100", "300");
        var b = row("2835", "0", "600", "0", "0", "100", "0", null);
        var all = RobinhoodQuarterlyStatementsService.combine(List.of(a, b));
        assertEquals(0, d("1000").compareTo(all.openingValue()));
        assertEquals(0, d("2300").compareTo(all.closingValue()));
        // Net money moved in from outside is zero, so market change is the whole 1300.
        assertEquals(0, d("1300").compareTo(all.marketChange()));
    }

    @Test
    void combinedHasNoMarketChangeWhenAnAccountLacksOpening() {
        var a = row("3370", "1000", "1700", "0", "0", "0", "0", "300");
        var b = new RobinhoodStatementDto(
                "3550", "x", null, LocalDate.of(2026, 9, 30), null, d("50"), d("0"), d("0"), d("0"), d("0"), null,
                d("0"), null, null, null, null, null, null);
        var all = RobinhoodQuarterlyStatementsService.combine(List.of(a, b));
        assertNull(all.openingValue());
        assertNull(all.marketChange());
    }
}
