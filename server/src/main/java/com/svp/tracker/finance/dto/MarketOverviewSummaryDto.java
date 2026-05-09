package com.svp.tracker.finance.dto;

/**
 * High-level US tone from headline indexes (session day % only). VIX helps read risk appetite; delayed / indicative
 * only.
 */
public record MarketOverviewSummaryDto(
        String narrative,
        Double vixLevel,
        Double vixChangePercentDay,
        Double sp500ChangePercentDay,
        Double nasdaqCompositeChangePercentDay,
        Double dowChangePercentDay,
        Double russell2000ChangePercentDay) {}
