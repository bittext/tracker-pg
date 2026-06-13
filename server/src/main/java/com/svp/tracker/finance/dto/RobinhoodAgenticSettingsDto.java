package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticSettingsDto(
        boolean executionEnabled,
        boolean requireApproval,
        BigDecimal maxOrderNotional,
        String allowedSymbols,
        Instant updatedAt) {}
