package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RobinhoodRhDailyTrackerSnapshotWindowTest {

    @Test
    void monthWindowStartsAWeekBeforeTheFirstSelectedMonth() {
        var window = RobinhoodRhDailyTrackerService.snapshotQueryWindow(2026, Set.of(9));
        assertEquals(LocalDate.of(2026, 8, 25), window.start());
        assertEquals(LocalDate.of(2026, 9, 30), window.end());
        assertEquals(LocalDate.of(2026, 9, 1), window.monthStart());
    }

    @Test
    void emptyOrFullSelectionKeepsTheCalendarYear() {
        var empty = RobinhoodRhDailyTrackerService.snapshotQueryWindow(2026, Set.of());
        assertEquals(LocalDate.of(2026, 1, 1), empty.start());
        assertEquals(LocalDate.of(2026, 12, 31), empty.end());

        var all = RobinhoodRhDailyTrackerService.snapshotQueryWindow(
                2026, Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        assertEquals(LocalDate.of(2026, 1, 1), all.start());
        assertEquals(LocalDate.of(2026, 12, 31), all.end());
    }
}
