package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticBankingTransactionDto(
        String externalId,
        String merchantName,
        String description,
        BigDecimal amountUsd,
        String transactionStatus,
        Instant transactionAt) {}
