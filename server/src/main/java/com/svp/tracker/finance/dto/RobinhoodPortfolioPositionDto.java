package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

/** One row in the portfolio positions table (Robinhood-style). */
public record RobinhoodPortfolioPositionDto(
        String instrument,
        String name,
        String contract,
        String assetClass,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal marketPrice,
        BigDecimal marketValue,
        BigDecimal openPnL,
        BigDecimal dayOpenPnL,
        Double dayOpenPnLPercent) {}
