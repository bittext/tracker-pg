package com.svp.tracker.finance.dto;

/** One reported quarter merged from Alpha Vantage INCOME_STATEMENT + EARNINGS. */
public record CompanyFinancialsQuarterDto(
        String fiscalDateEnding,
        Double revenue,
        Double netIncome,
        Double grossProfit,
        Double operatingIncome,
        Double netMarginPct,
        Double epsActual,
        Double epsEstimate,
        Double epsSurprisePct) {}
