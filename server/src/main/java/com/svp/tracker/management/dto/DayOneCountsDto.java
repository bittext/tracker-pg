package com.svp.tracker.management.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DayOneCountsDto {
    int year;
    Integer month;
    Integer day;
    long entriesInYear;
    long entriesInMonth;
    long entriesOnSelectedDay;
}
