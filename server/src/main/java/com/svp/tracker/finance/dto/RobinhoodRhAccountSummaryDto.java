package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RobinhoodRhAccountSummaryDto(
        String accountNumberMasked,
        String accountSuffix,
        String label,
        /** INDIVIDUAL, AGENTIC, MANAGED, IRA, OTHER */
        String accountKind,
        boolean agenticAccount,
        boolean managedAccount,
        /** Total account value at tracking cutoff (Apr 5 2026 Central). */
        BigDecimal startingTotalValue,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal internalTransferIn,
        BigDecimal internalTransferOut,
        BigDecimal netCashFlow,
        List<RobinhoodRhCashFlowEventDto> cashFlowEvents,
        BigDecimal cashBalance,
        BigDecimal equityMarketValue,
        BigDecimal totalAccountValue,
        BigDecimal totalCostBasis,
        BigDecimal unrealizedPnL,
        /** totalAccountValue − startingTotalValue − netCashFlow since cutoff. */
        BigDecimal gainLossVsNetDeposits,
        boolean gainLossPositive,
        List<RobinhoodRhHoldingDto> holdings,
        Instant syncedAt,
        List<String> notes) {}
