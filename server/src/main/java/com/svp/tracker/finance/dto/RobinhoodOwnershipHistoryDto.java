package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Stock ownership history from Daily Tracker snapshots (updated by the nightly/hourly capture job). */
public record RobinhoodOwnershipHistoryDto(
        String symbol,
        String accountSuffix,
        String accountMasked,
        int year,
        String captureKind,
        /** Equity symbols seen in SCHEDULED snapshots for this account/year (for the UI picker). */
        List<String> availableSymbols,
        /** Account suffixes with SCHEDULED snapshots in the year. */
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
        List<RobinhoodOwnershipHistoryPointDto> points,
        List<String> notes) {}
