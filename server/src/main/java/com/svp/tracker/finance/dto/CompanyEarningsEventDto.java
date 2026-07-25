package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One earnings event from the Nasdaq calendar. */
public record CompanyEarningsEventDto(
        LocalDate reportDate,
        String symbol,
        String companyName,
        String marketCap,
        Long marketCapValue,
        String fiscalQuarterEnding,
        String epsForecast,
        String lastYearEps,
        String lastYearReportDate,
        String timing,
        boolean onWatchlist,
        String decisionStatus) {}
