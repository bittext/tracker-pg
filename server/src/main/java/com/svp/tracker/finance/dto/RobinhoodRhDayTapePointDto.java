package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One hourly (or scheduled) pull on an account, with fills and qty hops since the prior pull. */
public record RobinhoodRhDayTapePointDto(
        long snapshotId,
        Instant snapshotAt,
        String captureKind,
        BigDecimal totalAccountValue,
        BigDecimal cashBalance,
        BigDecimal equityMarketValue,
        BigDecimal valueChange,
        List<RobinhoodRhDailyTradeDto> trades,
        List<RobinhoodRhDailySnapshotHoldingDto> holdingMoves) {}
