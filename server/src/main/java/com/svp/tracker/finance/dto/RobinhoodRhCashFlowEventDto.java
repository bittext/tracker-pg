package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Deposit, withdrawal, starting balance, or internal RH account transfer since cutoff. */
public record RobinhoodRhCashFlowEventDto(
        LocalDate activityDate,
        String direction,
        BigDecimal amount,
        String transCode,
        String description,
        String source,
        /** STARTING_BALANCE, EXTERNAL_IN, EXTERNAL_OUT, INTERNAL_IN, INTERNAL_OUT, INTEREST, FEE, OTHER */
        String flowCategory,
        boolean internalTransfer,
        String counterpartyMasked) {}
