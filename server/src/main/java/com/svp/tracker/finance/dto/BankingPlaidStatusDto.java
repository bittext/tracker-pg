package com.svp.tracker.finance.dto;

import java.util.List;

public record BankingPlaidStatusDto(
        boolean plaidConfigured,
        boolean linked,
        String itemIdSuffix,
        /** Parsed from {@link com.svp.tracker.finance.domain.BankingPlaidItem#getConnectionSummary()}; empty when unlinked. */
        List<String> connectionSummary) {}
