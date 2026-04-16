package com.svp.tracker.fitness.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One exercise day log row for the daily report (notes + optional duration). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyExerciseLogLineDto {

    private Long id;
    private Long exerciseId;
    private String exerciseName;
    private String notes;
    private Integer durationMinutes;
}
