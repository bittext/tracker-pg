package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RhPredictDeskDto(
        Instant lastSyncedAt,
        BigDecimal openValue,
        BigDecimal openCost,
        BigDecimal realizedAll,
        BigDecimal realizedWeek,
        int closeCount,
        List<String> warnings,
        List<RhPredictCloseDto> closes,
        /** Latest individual ••••3370 buying power. Predict event contracts settle on this account. */
        BigDecimal buyingPower,
        Instant buyingPowerAsOf) {}
