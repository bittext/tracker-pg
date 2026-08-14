package com.svp.tracker.fitness.dto;

import java.time.LocalDate;

public record FitnessHabitStreakDayDto(
        int dayIndex, LocalDate date, boolean completed, boolean today, boolean future) {}
