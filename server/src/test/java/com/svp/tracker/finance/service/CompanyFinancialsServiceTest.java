package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.CompanyFinancialsQuarterDto;
import com.svp.tracker.finance.dto.CompanyFinancialsResponseDto;
import com.svp.tracker.finance.dto.CompanyFinancialsTrendDto;
import com.svp.tracker.finance.service.RobinhoodEarningsService.EarningsRow;
import com.svp.tracker.finance.service.RobinhoodFinancialsService.FinancialsRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyFinancialsServiceTest {

    @Test
    void rhOnlyUpcomingAndReportedDoesNotThrowAndSorts() {
        List<EarningsRow> rh = List.of(
                new EarningsRow(2026, 3, LocalDate.of(2026, 7, 30), 2.02, 1.89),
                new EarningsRow(2026, 4, LocalDate.of(2026, 10, 29), null, 1.98),
                new EarningsRow(2026, 2, LocalDate.of(2026, 4, 30), 2.01, 1.94));
        var merge = CompanyFinancialsService.applyRobinhoodPrimary(List.of(), rh);
        assertTrue(merge.usedRobinhood());
        assertFalse(merge.usedAlpha());
        assertEquals(3, merge.quarters().size());
        assertEquals("2026-03-26", merge.quarters().get(0).fiscalDateEnding());
        assertEquals(2.02, merge.quarters().get(1).epsActual());
        CompanyFinancialsTrendDto trend = new CompanyFinancialsTrendService().assess(merge.quarters());
        assertNotNull(trend.verdict());
    }

    @Test
    void leftoverAvWithNullFiscalDoesNotNpeOnSort() {
        List<CompanyFinancialsQuarterDto> av = new ArrayList<>();
        av.add(null);
        av.add(new CompanyFinancialsQuarterDto(null, 1d, 1d, null, null, 10d, 1d, 1d, 0d));
        av.add(new CompanyFinancialsQuarterDto("2026-06-28", 100d, 10d, 40d, 20d, 10d, 1.5, 1.4, 7.1));
        List<EarningsRow> rh = List.of(new EarningsRow(2026, 3, LocalDate.of(2026, 7, 30), 2.02, 1.89));
        var merge = CompanyFinancialsService.applyRobinhoodPrimary(av, rh);
        assertEquals(1, merge.quarters().size());
        assertEquals("2026-06-28", merge.quarters().get(0).fiscalDateEnding());
        assertEquals(100d, merge.quarters().get(0).revenue());
        assertEquals(2.02, merge.quarters().get(0).epsActual());
    }

    @Test
    void nonFiniteDoublesAreDroppedSoJacksonCanSerialize() throws Exception {
        CompanyFinancialsQuarterDto avq = new CompanyFinancialsQuarterDto(
                "2026-06-28",
                Double.POSITIVE_INFINITY,
                Double.NaN,
                1d,
                1d,
                Double.NEGATIVE_INFINITY,
                2.0,
                0.0,
                Double.NaN);
        var merge = CompanyFinancialsService.applyRobinhoodPrimary(
                List.of(avq), List.of(new EarningsRow(2026, 3, LocalDate.of(2026, 7, 30), 2.02, 1.89)));
        CompanyFinancialsQuarterDto q = merge.quarters().get(0);
        assertEquals(2.02, q.epsActual());
        assertEquals(1.89, q.epsEstimate());
        Arrays.asList(q.revenue(), q.netIncome(), q.netMarginPct(), q.epsSurprisePct())
                .forEach(v -> assertTrue(v == null || Double.isFinite(v)));
        CompanyFinancialsTrendDto trend = new CompanyFinancialsTrendService().assess(merge.quarters());
        String json = new ObjectMapper()
                .writeValueAsString(new CompanyFinancialsResponseDto(
                        "AAPL", "Apple", merge.quarters(), trend, merge.sourceLabel(), "2026-08-30T19:00:00Z"));
        assertFalse(json.contains("NaN"));
        assertFalse(json.contains("Infinity"));
    }

    @Test
    void hoodQ2PrefersRobinhoodFinancialsRevenueOverAlphaVantage() {
        CompanyFinancialsQuarterDto avq = new CompanyFinancialsQuarterDto(
                "2026-06-30", 535_000_000d, 561_000_000d, null, null, 104.9, 0.50, 0.40, 25d);
        FinancialsRow fin = new FinancialsRow(
                2026, 2, "2026-06-30", 1_308_000_000d, null, 561_000_000d, 42.89);
        EarningsRow eps = new EarningsRow(2026, 2, LocalDate.of(2026, 7, 29), 0.62, 0.41);
        var merge = CompanyFinancialsService.applyRobinhoodPrimary(List.of(avq), List.of(eps), List.of(fin));
        assertTrue(merge.usedFinancials());
        assertEquals(1, merge.quarters().size());
        CompanyFinancialsQuarterDto q = merge.quarters().get(0);
        assertEquals(1_308_000_000d, q.revenue());
        assertEquals(561_000_000d, q.netIncome());
        assertEquals(42.89, q.netMarginPct());
        assertEquals(0.62, q.epsActual());
        assertEquals(0.41, q.epsEstimate());
        assertTrue(merge.sourceLabel().contains("robinhood-financials"));
    }
}
