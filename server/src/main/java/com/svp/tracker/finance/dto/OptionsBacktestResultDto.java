package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record OptionsBacktestResultDto(
        String strategyId,
        String strategyName,
        String symbol,
        String notes,
        BigDecimal startingCapital,
        BigDecimal endingEquity,
        BigDecimal totalReturnPct,
        BigDecimal maxDrawdownPct,
        int tradeCount,
        int winCount,
        int lossCount,
        BigDecimal winRatePct,
        BigDecimal totalPremiumCollected,
        List<OptionsBacktestEquityPointDto> equityCurve,
        List<OptionsBacktestTradeDto> trades) {}
