package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.svp.tracker.management.dto.ManagementDueReportDto;
import com.svp.tracker.management.dto.ManagementDueReportDto.Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManagementDueReportSupportTest {

    @Test
    void monthYearAndLifetimeSplitSettledFromOpen() {
        List<Row> rows = List.of(
                row(1, 2025, 12, LocalDate.of(2025, 12, 1), "PAYABLE", "Old rent", true, true, "1200"),
                row(2, 2026, 10, LocalDate.of(2026, 10, 1), "PAYABLE", "Rent", true, true, "1400"),
                row(3, 2026, 10, LocalDate.of(2026, 10, 5), "PAYABLE", "Electric", true, false, "90"),
                row(4, 2026, 10, LocalDate.of(2026, 10, 15), "RECEIVABLE", "Payroll", true, true, "4200"),
                row(5, 2026, 9, LocalDate.of(2026, 9, 15), "RECEIVABLE", "Payroll", true, true, "4100"));

        ManagementDueReportDto report = ManagementDueReportSupport.build(2026, 10, rows);

        assertEquals(money("2600"), report.lifetime().paid());
        assertEquals(money("8300"), report.lifetime().received());
        assertEquals(money("5700"), report.lifetime().net());
        assertEquals(LocalDate.of(2025, 12, 1), report.lifetime().firstSettledOn());

        assertEquals(money("1400"), report.yearTotals().paid());
        assertEquals(money("8300"), report.yearTotals().received());
        assertEquals(money("90"), report.yearTotals().openPayable());
        assertEquals(1, report.yearTotals().openPayableCount());

        assertEquals(money("1400"), report.monthTotals().paid());
        assertEquals(money("4200"), report.monthTotals().received());
        assertEquals(money("2800"), report.monthTotals().net());
        assertEquals(money("90"), report.monthTotals().openPayable());
        assertEquals("Rent", report.monthTotals().biggestOutName());
        assertEquals("Payroll", report.monthTotals().biggestInName());
        assertEquals(1, report.months().get(9).openCount());
        assertEquals(money("1400"), report.months().get(9).paid());
        assertEquals(1, report.who().stream().filter(w -> "Rent".equals(w.counterparty())).count());
    }

    @Test
    void biggestFallsBackToOpenWhenNothingIsSettled() {
        List<Row> rows = List.of(
                row(1, 2026, 10, LocalDate.of(2026, 10, 8), "PAYABLE", "Dentist", false, false, "250"),
                row(2, 2026, 10, LocalDate.of(2026, 10, 12), "RECEIVABLE", "Invoice", false, false, "80"));

        ManagementDueReportDto report = ManagementDueReportSupport.build(2026, 10, rows);

        assertEquals(money("0"), report.monthTotals().paid());
        assertEquals("Dentist", report.monthTotals().biggestOutName());
        assertEquals(money("250"), report.monthTotals().biggestOutAmount());
        assertEquals("Invoice", report.monthTotals().biggestInName());
        assertNull(report.lifetime().firstSettledOn());
        assertEquals(2, report.days().size());
        assertEquals(money("250"), report.days().getFirst().paid());
        assertEquals(money("80"), report.days().get(1).received());
    }

    private static Row row(
            long id,
            int year,
            int month,
            LocalDate date,
            String side,
            String name,
            boolean recurring,
            boolean settled,
            String amount) {
        return new Row(
                id,
                settled ? id : null,
                year,
                month,
                date,
                side,
                name,
                recurring,
                "",
                settled,
                money(amount),
                settled ? "settled" : "override",
                "");
    }

    private static BigDecimal money(String raw) {
        return new BigDecimal(raw).setScale(2);
    }
}
