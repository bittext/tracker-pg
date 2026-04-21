package com.svp.tracker.management.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DayOneCalendarDayDto {
    LocalDate date;
    int entryCount;
    /** 0 = none, 1–4 = intensity band for UI coloring. */
    int level;
}
