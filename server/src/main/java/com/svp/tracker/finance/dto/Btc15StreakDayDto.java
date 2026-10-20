package com.svp.tracker.finance.dto;

import java.time.LocalDate;

public record Btc15StreakDayDto(
        LocalDate date,
        String label,
        int windows,
        int below,
        int followed,
        int consecutiveRuns,
        int singles,
        int run2,
        int run3,
        int run4,
        int run5,
        int run6,
        int run7) {}
