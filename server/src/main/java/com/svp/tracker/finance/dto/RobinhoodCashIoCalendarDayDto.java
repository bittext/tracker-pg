package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RobinhoodCashIoCalendarDayDto(
        LocalDate date, BigDecimal totalIn, BigDecimal totalOut, BigDecimal net, int entryCount) {}
