package com.svp.tracker.finance.dto;

import java.time.LocalDate;

public record Btc15StreakRunDto(
        LocalDate date,
        String dayLabel,
        int length,
        int followed,
        String fromLabel,
        String toLabel) {}
