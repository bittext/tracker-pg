package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodRhDailySnapshotDetailDto(
        long id,
        LocalDate snapshotDate,
        Instant snapshotAt,
        String captureKind,
        LocalDate periodStartDate,
        String accountSuffix,
        String label,
        String accountKind,
        BigDecimal totalAccountValue,
        BigDecimal cashBalance,
        BigDecimal equityMarketValue,
        BigDecimal periodAdded,
        BigDecimal periodRemoved,
        BigDecimal periodValueChange,
        List<RobinhoodRhHoldingDto> holdings,
        List<RobinhoodRhCashFlowEventDto> periodFlows) {}
