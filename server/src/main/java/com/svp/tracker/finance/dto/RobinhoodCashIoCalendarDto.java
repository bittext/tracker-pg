package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record RobinhoodCashIoCalendarDto(
        int year,
        Integer month,
        String accountSuffix,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal net,
        List<RobinhoodCashIoCalendarDayDto> days) {}
