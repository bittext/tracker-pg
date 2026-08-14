package com.svp.tracker.fitness.dto;

import java.util.List;

public record FitnessHabitStreakHabitDto(
        String kind, String title, String subtitle, int completedCount, List<FitnessHabitStreakDayDto> days) {}
