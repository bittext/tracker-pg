package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RobinhoodCashIoYtdEventDto(
        LocalDate date,
        String kind,
        BigDecimal amount,
        String note,
        BigDecimal runningAdjusted) {}
