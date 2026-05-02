package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record BankingTransactionDto(
        long id,
        long institutionId,
        String institutionName,
        long importFileId,
        String txnDate,
        BigDecimal amount,
        String description,
        /**
         * {@code CREDIT} for positive amount (inflow), {@code DEBIT} for negative (outflow), {@code ZERO} when amount
         * is zero — matches stored import sign (OFX, CSV, Excel, QIF, etc.).
         */
        String debitCredit,
        /** Uppercase style label from upload filename extension, e.g. CSV, QFX, XLSX. */
        String sourceFormat) {}
