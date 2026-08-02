package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ownership history from Daily Tracker snapshots (updated by the nightly/hourly capture job).
 * {@code assetKind} is {@code equity} (share counts) or {@code option} (contracts from 2026-06-28).
 */
public record RobinhoodOwnershipHistoryDto(
        /** {@code equity} or {@code option}. */
        String assetKind,
        String symbol,
        /** Selected option contract key when {@code assetKind=option}; null for equity / all-contracts view. */
        String contractKey,
        String contractLabel,
        String accountSuffix,
        String accountMasked,
        int year,
        LocalDate fromDate,
        String captureKind,
        /** Equity symbols seen in snapshots for this account/range (UI picker). */
        List<String> availableSymbols,
        /** Option contracts seen in snapshots for this account/range (UI picker / overview). */
        List<RobinhoodOwnershipContractDto> availableContracts,
        /** Account suffixes with snapshots in the range. */
        List<String> availableAccountSuffixes,
        LocalDate highDate,
        BigDecimal highQuantity,
        LocalDate lowDate,
        BigDecimal lowQuantity,
        BigDecimal latestQuantity,
        BigDecimal latestOwnSharesEstimate,
        BigDecimal latestMarginSharesEstimate,
        BigDecimal latestMarginLoan,
        BigDecimal latestMarketValue,
        BigDecimal latestCostBasis,
        /** Selected series (equity symbol or one contract). Empty when options all-contracts overview. */
        List<RobinhoodOwnershipHistoryPointDto> points,
        /** All option contract series when {@code assetKind=option} and no {@code contractKey}. */
        List<RobinhoodOwnershipContractSeriesDto> contractSeries,
        List<String> notes) {}
