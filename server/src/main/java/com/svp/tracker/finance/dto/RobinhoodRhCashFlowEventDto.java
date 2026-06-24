package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Deposit or withdrawal since the RH Accounts Track cutoff. */
public record RobinhoodRhCashFlowEventDto(
        LocalDate activityDate,
        String direction,
        BigDecimal amount,
        String transCode,
        String description,
        String source) {}
