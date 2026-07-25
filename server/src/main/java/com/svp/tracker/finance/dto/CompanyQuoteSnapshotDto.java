package com.svp.tracker.finance.dto;

import java.util.List;

public record CompanyQuoteSnapshotDto(
        String symbol,
        String companyName,
        String lastSalePrice,
        String netChange,
        String percentageChange,
        String deltaIndicator,
        String exchange,
        String marketStatus,
        String fiftyTwoWeekRange,
        String upcomingEarningsMessage) {}
