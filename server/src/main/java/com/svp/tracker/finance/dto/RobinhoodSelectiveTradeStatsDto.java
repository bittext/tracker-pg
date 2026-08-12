package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodSelectiveTradeStatsDto(
        int total,
        int worked,
        int didnt,
        int mixed,
        /** worked / (worked + didnt); null when no decisive outcomes. */
        BigDecimal successRate,
        long distinctDays,
        BigDecimal avgPerActiveDay,
        BigDecimal avgPerMonthInPeriod) {}
