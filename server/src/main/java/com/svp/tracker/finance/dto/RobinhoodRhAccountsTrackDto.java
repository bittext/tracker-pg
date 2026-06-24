package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RobinhoodRhAccountsTrackDto(
        Instant trackingStartedAt,
        List<RobinhoodRhAccountSummaryDto> accounts,
        BigDecimal combinedTotalValue,
        BigDecimal combinedNetCashFlow,
        BigDecimal combinedGainLossVsNetDeposits,
        boolean combinedGainLossPositive,
        List<String> notes) {}
