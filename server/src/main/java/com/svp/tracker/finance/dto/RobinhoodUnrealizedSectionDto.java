package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Open positions and mark-to-market unrealized P&amp;L as of a date. */
public record RobinhoodUnrealizedSectionDto(
        LocalDate asOfDate,
        BigDecimal totalCostBasis,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnL,
        int openLotCount,
        int quotedLotCount,
        boolean truncated,
        String note,
        List<RobinhoodOpenPositionDto> openPositions) {}
