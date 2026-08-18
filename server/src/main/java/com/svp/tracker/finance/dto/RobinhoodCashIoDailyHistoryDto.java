package com.svp.tracker.finance.dto;

import java.util.List;

public record RobinhoodCashIoDailyHistoryDto(
        String accountSuffix,
        String accountLabel,
        int year,
        List<RobinhoodCashIoDailyDto> days) {}
