package com.svp.tracker.fitness.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseDayBreakdownDto {

    private Long exerciseId;
    private String exerciseName;
    private int logCount;
}
