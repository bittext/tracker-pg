package com.svp.tracker.management.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class ManagementMailSlotTest {

    @Test
    void windowsCoverMorningMiddayAndEvening() {
        LocalDate day = LocalDate.of(2026, 11, 7);
        assertEquals(
                ZonedDateTime.of(day.minusDays(1), LocalTime.of(19, 0), ManagementMailSlot.ZONE),
                ManagementMailSlot.MORNING.windowStart(day));
        assertEquals(
                ZonedDateTime.of(day, LocalTime.of(7, 0), ManagementMailSlot.ZONE),
                ManagementMailSlot.MIDDAY.windowStart(day));
        assertEquals(
                ZonedDateTime.of(day, LocalTime.of(13, 0), ManagementMailSlot.ZONE),
                ManagementMailSlot.EVENING.windowStart(day));
    }

    @Test
    void hourPicksTheSlot() {
        assertEquals(ManagementMailSlot.MORNING, ManagementMailSlot.forNow(at(8)));
        assertEquals(ManagementMailSlot.MIDDAY, ManagementMailSlot.forNow(at(13)));
        assertEquals(ManagementMailSlot.EVENING, ManagementMailSlot.forNow(at(19)));
    }

    private static ZonedDateTime at(int hour) {
        return ZonedDateTime.of(LocalDate.of(2026, 11, 7), LocalTime.of(hour, 0), ManagementMailSlot.ZONE);
    }
}
