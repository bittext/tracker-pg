package com.svp.tracker.finance.dto;

/**
 * Fundamentals / positioning snapshot for company research (Alpha Vantage OVERVIEW when configured).
 * Short and ownership figures are point-in-time, not a daily time series.
 */
public record CompanyResearchFundamentalsDto(
        String source,
        String name,
        String description,
        String sector,
        String industry,
        String marketCap,
        String peRatio,
        String forwardPe,
        String pegRatio,
        String eps,
        String profitMargin,
        String operatingMargin,
        String roe,
        String revenueTtm,
        String bookValue,
        String dividendYield,
        String beta,
        String week52High,
        String week52Low,
        String analystTarget,
        String shortRatio,
        String shortPercentFloat,
        String shortPercentOutstanding,
        String percentInsiders,
        String percentInstitutions,
        String sharesOutstanding,
        String sharesFloat,
        /** Human notes about coverage gaps (e.g. no industry average PE from this source). */
        String coverageNote) {}
