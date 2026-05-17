package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodInstrumentPerformanceDto(
        String instrument,
        BigDecimal totalRealizedPnL,
        int closedLots,
        int winCount,
        int lossCount) {}
