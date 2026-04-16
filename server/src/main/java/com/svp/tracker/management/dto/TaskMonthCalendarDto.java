package com.svp.tracker.management.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMonthCalendarDto {

    private int year;
    private int month;
    /** ISO date (yyyy-MM-dd) → tasks due that day. */
    private Map<String, List<ManagementTaskDto>> tasksByDay;
}
