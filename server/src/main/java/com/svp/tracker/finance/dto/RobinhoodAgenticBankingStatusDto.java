package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticBankingStatusDto(
        boolean featureEnabled,
        boolean serviceConfigured,
        boolean connected,
        String cardLastFour,
        String cardStatus,
        String activationStatus,
        BigDecimal monthlyLimitUsd,
        BigDecimal totalSpendUsd,
        BigDecimal availableBalanceUsd,
        Instant connectedAt,
        Instant lastSyncAt,
        String lastSyncStatus,
        String lastSyncMessage,
        int transactionCount) {}
