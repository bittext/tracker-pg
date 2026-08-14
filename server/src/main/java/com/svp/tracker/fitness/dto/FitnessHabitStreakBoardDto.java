package com.svp.tracker.fitness.dto;

import java.time.LocalDate;
import java.util.List;

public record FitnessHabitStreakBoardDto(
        LocalDate startDate,
        LocalDate endDate,
        int dayCount,
        LocalDate today,
        List<FitnessHabitStreakHabitDto> habits) {}
