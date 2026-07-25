package com.svp.tracker.finance.dto;

import java.util.List;

/** Request to generate or refresh an AI speculative outlook for saved scenarios. */
public record InvestmentThenNowOutlookRequestDto(
        /** When empty/null, use all saved scenarios for the owner. */
        List<Long> scenarioIds,
        /** 3, 6, or 12 — default 6. */
        Integer horizonMonths,
        /** When true, regenerate even if a cached outlook exists. */
        Boolean force) {}
