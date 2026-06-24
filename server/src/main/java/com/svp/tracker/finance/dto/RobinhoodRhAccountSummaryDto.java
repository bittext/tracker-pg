package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RobinhoodRhAccountSummaryDto(
        String accountNumberMasked,
        String accountSuffix,
        String label,
        boolean agenticAccount,
        /** Deposits / transfers in since cutoff (CSV for default individual; may be empty for Agentic-only). */
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal netCashFlow,
        List<RobinhoodRhCashFlowEventDto> cashFlowEvents,
        BigDecimal cashBalance,
        BigDecimal equityMarketValue,
        BigDecimal totalAccountValue,
        BigDecimal totalCostBasis,
        BigDecimal unrealizedPnL,
        /** totalAccountValue − netCashFlow since cutoff (positive = ahead of net deposits). */
        BigDecimal gainLossVsNetDeposits,
        boolean gainLossPositive,
        List<RobinhoodRhHoldingDto> holdings,
        Instant syncedAt,
        List<String> notes) {}
