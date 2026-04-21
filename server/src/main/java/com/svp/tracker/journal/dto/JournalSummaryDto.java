package com.svp.tracker.journal.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JournalSummaryDto {
    long totalCount;
    List<MonthCount> byMonth;
    List<DayCount> byDay;

    @Value
    @Builder
    public static class MonthCount {
        String yearMonth;
        long count;
    }

    @Value
    @Builder
    public static class DayCount {
        String date;
        long count;
    }
}
