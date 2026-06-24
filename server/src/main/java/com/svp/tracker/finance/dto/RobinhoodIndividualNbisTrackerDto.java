package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** NBIS reconciliation for the individual (••••3370) account since tracker cutoff. */
public record RobinhoodIndividualNbisTrackerDto(
        String accountMasked,
        Instant trackingStartedAt,
        BigDecimal baselineNbis,
        BigDecimal boughtSince,
        BigDecimal soldSince,
        BigDecimal expectedNbis,
        BigDecimal liveNbis,
        BigDecimal variance,
        Instant liveSyncedAt,
        boolean liveFromSync) {}
