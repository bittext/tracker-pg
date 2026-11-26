package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RobinhoodCashIoRequestDto(
        String accountSuffix, LocalDate activityDate, String direction, BigDecimal amount, String note) {}
