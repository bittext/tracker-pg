package com.svp.tracker.finance.dto;

import java.util.List;

/**
 * Rule-based read of the trailing quarterly trend (revenue, net margin, EPS beat/miss). This is
 * NOT an analyst consensus forecast — it is a deterministic summary of the observed history.
 */
public record CompanyFinancialsTrendDto(
        /** "Improving" | "Declining" | "Mixed" | "Insufficient history for a trend read" */
        String verdict,
        Integer score,
        /** "accelerating" | "stable" | "decelerating" | "contracting" | null when not computable */
        String revenueTrend,
        /** "expanding" | "flat" | "compressing" | null when not computable */
        String marginTrend,
        /** "consistent-beats" | "mixed" | "consistent-misses" | null when not computable */
        String epsTrend,
        /** Plain-English sentence(s) citing the actual computed numbers. */
        String narrative,
        List<String> warnings) {}
