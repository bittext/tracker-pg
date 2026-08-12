package com.svp.tracker.finance.dto;

import java.util.List;

public record RobinhoodSelectiveTradeAiInsightDto(
        String periodLabel,
        String model,
        String summary,
        List<String> trends,
        List<String> frequencyNotes,
        List<String> improvements,
        List<String> nextActions,
        RobinhoodSelectiveTradeStatsDto stats) {}
