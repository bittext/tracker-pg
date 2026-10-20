package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Btc15StreakDeskDto(
        String series,
        String title,
        LocalDate from,
        LocalDate to,
        int windows,
        int below,
        int above,
        int followed,
        int consecutiveRuns,
        int singles,
        int run2,
        int run3,
        int run4,
        int run5,
        int run6,
        int run7,
        int longest,
        BigDecimal belowRate,
        BigDecimal followRate,
        String pattern,
        String headline,
        List<String> notes,
        List<Btc15StreakDayDto> days,
        List<Btc15StreakRunDto> runs) {}
