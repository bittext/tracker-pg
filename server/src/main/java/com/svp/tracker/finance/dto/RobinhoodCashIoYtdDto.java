package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodCashIoYtdDto(
        String accountSuffix,
        String accountLabel,
        LocalDate startDate,
        String startAtLabel,
        BigDecimal startingCash,
        BigDecimal totalInputs,
        BigDecimal totalOutputs,
        BigDecimal netIo,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal adjustedValue,
        BigDecimal liveValue,
        LocalDate liveDate,
        List<RobinhoodCashIoYtdEventDto> events,
        List<RobinhoodCashIoYtdPointDto> adjustedSeries) {}
