package com.svp.tracker.fitness.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MonthlyExerciseReportDto {

    private int year;
    private int month;
    private int totalLogs;
    /** Distinct calendar days with at least one {@link com.svp.tracker.fitness.domain.ExerciseDayLog} in this month. */
    private int workoutDays;
    /**
     * Same distinct-day count as {@link #workoutDays}, computed explicitly from {@code ExerciseDayLog} rows (for
     * “Active days (Exercise logs)” KPI).
     */
    private int exerciseLogActiveDays;
    private List<ExerciseMonthBreakdownDto> exercises = new ArrayList<>();
}
