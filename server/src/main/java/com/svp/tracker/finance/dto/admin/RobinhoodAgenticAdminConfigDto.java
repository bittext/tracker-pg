package com.svp.tracker.finance.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticAdminConfigDto(
        boolean featureEnabled,
        boolean serviceConfigured,
        boolean executionEnabled,
        boolean autoTradeServerEnabled,
        String serviceBaseUrl,
        boolean syncCronEnabled,
        String syncCron,
        BigDecimal serverDefaultMaxOrderNotional,
        long autoTradePollMs) {}
