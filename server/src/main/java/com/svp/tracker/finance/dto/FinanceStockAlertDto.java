package com.svp.tracker.finance.dto;

import com.svp.tracker.finance.domain.FinanceStockAlertRepeatMode;
import com.svp.tracker.finance.domain.FinanceStockAlertTriggerType;
import java.math.BigDecimal;
import java.time.Instant;

public record FinanceStockAlertDto(
        Long id,
        String symbol,
        String companyName,
        FinanceStockAlertTriggerType triggerType,
        BigDecimal thresholdValue,
        FinanceStockAlertRepeatMode repeatMode,
        int cooldownMinutes,
        boolean enabled,
        boolean triggerArmed,
        Instant lastCheckedAt,
        Instant lastTriggeredAt,
        BigDecimal lastRegularMarketPrice,
        BigDecimal lastRegularMarketChangePercent,
        int fireCount,
        Instant createdAt,
        Instant updatedAt) {}
