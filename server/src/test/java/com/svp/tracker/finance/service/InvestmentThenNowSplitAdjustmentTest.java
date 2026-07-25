package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.svp.tracker.finance.service.InvestmentThenNowService.SplitEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class InvestmentThenNowSplitAdjustmentTest {

    @Test
    void applySplitAdjustment_dividesPreSplitClosesByLaterFactors() {
        NavigableMap<LocalDate, Double> raw = new TreeMap<>();
        raw.put(LocalDate.of(2020, 8, 28), 500.0); // before 4:1
        raw.put(LocalDate.of(2020, 8, 31), 125.0); // split day (already post-split)
        raw.put(LocalDate.of(2020, 9, 1), 130.0);

        List<SplitEvent> splits = List.of(new SplitEvent(LocalDate.of(2020, 8, 31), 4.0));

        NavigableMap<LocalDate, Double> adj = InvestmentThenNowService.applySplitAdjustment(raw, splits);

        assertEquals(125.0, adj.get(LocalDate.of(2020, 8, 28)), 1e-9);
        assertEquals(125.0, adj.get(LocalDate.of(2020, 8, 31)), 1e-9);
        assertEquals(130.0, adj.get(LocalDate.of(2020, 9, 1)), 1e-9);
    }

    @Test
    void applySplitAdjustment_compoundsMultipleLaterSplits() {
        NavigableMap<LocalDate, Double> raw = new TreeMap<>();
        raw.put(LocalDate.of(2010, 1, 1), 400.0);

        List<SplitEvent> splits = List.of(
                new SplitEvent(LocalDate.of(2014, 6, 9), 7.0),
                new SplitEvent(LocalDate.of(2020, 8, 31), 4.0));

        NavigableMap<LocalDate, Double> adj = InvestmentThenNowService.applySplitAdjustment(raw, splits);

        assertEquals(400.0 / 28.0, adj.get(LocalDate.of(2010, 1, 1)), 1e-9);
    }

    @Test
    void applySplitAdjustment_noSplits_returnsSameMap() {
        NavigableMap<LocalDate, Double> raw = new TreeMap<>();
        raw.put(LocalDate.of(2026, 6, 28), 190.0);

        NavigableMap<LocalDate, Double> adj = InvestmentThenNowService.applySplitAdjustment(raw, List.of());

        assertEquals(raw, adj);
    }
}
