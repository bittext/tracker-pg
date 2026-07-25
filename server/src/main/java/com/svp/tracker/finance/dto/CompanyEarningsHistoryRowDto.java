package com.svp.tracker.finance.dto;

import java.util.List;

public record CompanyEarningsHistoryRowDto(
        String fiscalQuarterEnd, String dateReported, String eps, String consensusForecast, String percentageSurprise) {}
