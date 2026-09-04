package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodRhPeriodBalanceRowDto(
        String key,
        String label,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean currentPeriod,
        BigDecimal combinedStart,
        BigDecimal combinedEnd,
        BigDecimal combinedChange,
        List<RobinhoodRhPeriodAccountFigureDto> accounts) {}
