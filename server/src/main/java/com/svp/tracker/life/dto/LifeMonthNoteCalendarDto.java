package com.svp.tracker.life.dto;

import java.util.List;

public record LifeMonthNoteCalendarDto(int year, List<MonthCount> months) {
    public record MonthCount(int month, long noteCount) {}
}
