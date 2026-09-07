package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManagementDueCalendarSupportTest {

    @Test
    void occurrenceDateClampsToLastDayOfMonth() {
        assertEquals(LocalDate.of(2026, 2, 28), ManagementDueCalendarSupport.occurrenceDate(2026, 2, 31));
        assertEquals(LocalDate.of(2026, 9, 15), ManagementDueCalendarSupport.occurrenceDate(2026, 9, 15));
    }

    @Test
    void recurringAppearsFromStartMonthForward() {
        YearMonth sept = YearMonth.of(2026, 9);
        assertTrue(ManagementDueCalendarSupport.appearsInMonth(true, LocalDate.of(2026, 9, 1), null, sept));
        assertTrue(ManagementDueCalendarSupport.appearsInMonth(true, LocalDate.of(2026, 8, 1), null, sept));
        assertFalse(ManagementDueCalendarSupport.appearsInMonth(true, LocalDate.of(2026, 10, 1), null, sept));
    }

    @Test
    void oneOffAppearsOnlyInItsMonth() {
        LocalDate date = LocalDate.of(2026, 9, 12);
        assertTrue(ManagementDueCalendarSupport.appearsInMonth(false, LocalDate.of(2026, 1, 1), date, YearMonth.of(2026, 9)));
        assertFalse(ManagementDueCalendarSupport.appearsInMonth(false, LocalDate.of(2026, 1, 1), date, YearMonth.of(2026, 10)));
    }

    @Test
    void payeeMatchesIgnoresBankNoise() {
        assertTrue(ManagementDueCalendarSupport.payeeMatches("ComEd", "ACH DEBIT COMED PAYMENT"));
        assertTrue(ManagementDueCalendarSupport.payeeMatches("Acme Property", "ACME PROPERTY 15"));
        assertFalse(ManagementDueCalendarSupport.payeeMatches("ComEd", "NETFLIX.COM"));
    }

    @Test
    void medianUsesMiddleValue() {
        assertEquals(
                new BigDecimal("120.00"),
                ManagementDueCalendarSupport.median(
                        List.of(new BigDecimal("100"), new BigDecimal("120"), new BigDecimal("200"))));
        assertEquals(
                new BigDecimal("15.50"),
                ManagementDueCalendarSupport.median(List.of(new BigDecimal("10"), new BigDecimal("21"))));
    }
}
