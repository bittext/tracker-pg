package com.svp.tracker.finance.dto;

/** Heuristic market-read from latest validated headlines. */
public record StockNewsAnalysisDto(
        String overallSentiment,
        double sentimentScore,
        int projectedGrowthPercent,
        String projectedGrowthLabel,
        StockNewsStressSignalsDto stressSignals) {}
