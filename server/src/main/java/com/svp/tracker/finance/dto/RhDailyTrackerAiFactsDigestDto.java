package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

/** Small KPI chips shown with AI insights (deterministic, not LLM-invented). */
public record RhDailyTrackerAiFactsDigestDto(
        int tradeCount,
        int buyCount,
        int sellCount,
        int uniqueSymbols,
        int activeTradeDays,
        BigDecimal accountValueStart,
        BigDecimal accountValueEnd,
        BigDecimal accountValueChange,
        BigDecimal periodAdded,
        BigDecimal periodRemoved,
        List<String> topSymbolsByCount,
        List<String> topSymbolsByNotional) {}
