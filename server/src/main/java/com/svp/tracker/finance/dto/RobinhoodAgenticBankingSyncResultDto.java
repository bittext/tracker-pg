package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticBankingSyncResultDto(
        boolean ok,
        Instant syncedAt,
        String cardLastFour,
        String cardStatus,
        String activationStatus,
        BigDecimal monthlyLimitUsd,
        BigDecimal totalSpendUsd,
        BigDecimal availableBalanceUsd,
        int transactionCount,
        String message) {}
