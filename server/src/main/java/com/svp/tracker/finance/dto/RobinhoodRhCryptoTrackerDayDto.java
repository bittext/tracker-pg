package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One capture cycle in the crypto tracker timeline (future use). */
public record RobinhoodRhCryptoTrackerDayDto(
        LocalDate snapshotDate,
        Instant snapshotAt,
        String captureKind,
        BigDecimal totalValue,
        BigDecimal changeFromPrevious,
        List<RobinhoodRhCryptoHoldingDto> holdings) {}
