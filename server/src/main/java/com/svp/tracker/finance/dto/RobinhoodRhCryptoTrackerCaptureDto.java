package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RobinhoodRhCryptoTrackerCaptureDto(
        Instant snapshotAt,
        String captureKind,
        BigDecimal totalValue,
        List<RobinhoodRhCryptoTrackerAccountCellDto> accounts) {}
