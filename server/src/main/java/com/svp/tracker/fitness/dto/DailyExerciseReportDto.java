package com.svp.tracker.fitness.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DailyExerciseReportDto {

    private LocalDate date;
    private int totalLogs;
    private BigDecimal bodyWeightKg;
    private List<ExerciseDayBreakdownDto> exercises = new ArrayList<>();
    /** Individual log lines for the day (exercise name, duration, notes). */
    private List<DailyExerciseLogLineDto> logLines = new ArrayList<>();
}
