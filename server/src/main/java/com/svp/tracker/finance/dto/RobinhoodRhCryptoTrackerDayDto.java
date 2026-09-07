package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One Central calendar day: official close plus hourly/manual pulls. */
public record RobinhoodRhCryptoTrackerDayDto(
        LocalDate snapshotDate,
        Instant snapshotAt,
        String captureKind,
        BigDecimal totalValue,
        BigDecimal changeFromPrevious,
        List<RobinhoodRhCryptoHoldingDto> holdings,
        List<RobinhoodRhCryptoTrackerAccountCellDto> accounts,
        List<RobinhoodRhCryptoTrackerCaptureDto> intradayCaptures,
        List<RobinhoodRhCryptoTrackerCaptureDto> manualCaptures) {}
