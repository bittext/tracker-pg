package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record BankingTransactionDto(
        long id,
        long institutionId,
        String institutionName,
        long importFileId,
        String txnDate,
        BigDecimal amount,
        String description) {}
