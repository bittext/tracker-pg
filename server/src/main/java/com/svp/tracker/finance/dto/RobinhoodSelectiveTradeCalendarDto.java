package com.svp.tracker.finance.dto;

import java.util.List;

public record RobinhoodSelectiveTradeCalendarDto(
        int year,
        Integer month,
        RobinhoodSelectiveTradeStatsDto stats,
        List<RobinhoodSelectiveTradeCalendarDayDto> days) {}
