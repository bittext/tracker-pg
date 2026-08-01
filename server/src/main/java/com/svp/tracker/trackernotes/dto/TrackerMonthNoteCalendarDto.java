package com.svp.tracker.trackernotes.dto;

import java.util.List;

public record TrackerMonthNoteCalendarDto(int year, List<MonthCount> months) {
    public record MonthCount(int month, long noteCount) {}
}
