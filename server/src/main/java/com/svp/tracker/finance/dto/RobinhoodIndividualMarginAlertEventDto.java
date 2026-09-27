package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodIndividualMarginAlertEventDto(
        Long id,
        String eventKind,
        BigDecimal bufferPercent,
        BigDecimal borrowPercent,
        String emailStatus,
        String destinationMasked,
        String detail,
        Instant createdAt) {}
