package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record Btc15WindowDto(
        String ticker,
        String label,
        Instant windowStart,
        Instant windowEnd,
        BigDecimal target,
        BigDecimal startPrice,
        BigDecimal endPrice,
        BigDecimal startGap,
        BigDecimal endGap,
        Boolean aboveTarget,
        /** Latest Coinbase print while the window is still unsettled; null once settled. */
        BigDecimal currentPrice,
        BigDecimal currentGap,
        /** Provisional above/below from the live print; null once settled. */
        Boolean liveAbove,
        boolean unsettled) {}
