package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

public record BankingPlaidStatusDto(
        boolean plaidConfigured,
        boolean linked,
        String itemIdSuffix,
        /** Parsed from {@link com.svp.tracker.finance.domain.BankingPlaidItem#getConnectionSummary()}; empty when unlinked. */
        List<String> connectionSummary,
        boolean dailyBalanceSync,
        /** Latest saved Plaid balance date for this institution, if any. */
        LocalDate lastBalanceDate) {}
