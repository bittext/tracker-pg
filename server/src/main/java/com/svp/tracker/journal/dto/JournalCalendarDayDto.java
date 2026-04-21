package com.svp.tracker.journal.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JournalCalendarDayDto {
    LocalDate date;
    int entryCount;
    /** 0 = empty … 4 = busiest in month (client maps to color). */
    int level;
}
