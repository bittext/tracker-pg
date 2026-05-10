package com.svp.tracker.management.dto;

import java.util.List;

/** Entry counts per calendar day for a year (sparse: only days with at least one entry). */
public record ManagementWorkLogCalendarDto(int year, List<DayCount> days) {

    public record DayCount(String date, long count) {}
}
