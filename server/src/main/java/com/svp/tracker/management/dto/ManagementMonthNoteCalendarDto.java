package com.svp.tracker.management.dto;

import java.util.List;

public record ManagementMonthNoteCalendarDto(int year, List<MonthCount> months) {

    public record MonthCount(int month, long noteCount) {}
}
