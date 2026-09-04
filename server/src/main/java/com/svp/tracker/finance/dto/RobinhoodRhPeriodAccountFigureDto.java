package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RobinhoodRhPeriodAccountFigureDto(
        String accountSuffix,
        BigDecimal start,
        BigDecimal end,
        BigDecimal change,
        LocalDate startDate,
        LocalDate endDate) {}
