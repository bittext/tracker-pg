package com.svp.tracker.finance.dto;

import java.util.List;

public record RobinhoodPerformanceInsightsDto(
        List<RobinhoodInstrumentPerformanceDto> bestPerformingStocks,
        List<RobinhoodClosedTradeDto> worstTrades,
        double averageHoldDays,
        int medianHoldDays,
        RobinhoodTradingFrequencyDto tradingFrequency,
        List<RobinhoodStrategyPerformanceDto> strategyPerformance) {}
