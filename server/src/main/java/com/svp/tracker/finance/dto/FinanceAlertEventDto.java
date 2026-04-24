package com.svp.tracker.finance.dto;

import com.svp.tracker.finance.domain.FinanceAlertDeliveryChannel;
import com.svp.tracker.finance.domain.FinanceAlertDeliveryStatus;
import com.svp.tracker.finance.domain.FinanceStockAlertTriggerType;
import java.math.BigDecimal;
import java.time.Instant;

public record FinanceAlertEventDto(
        Long id,
        Long alertId,
        String symbol,
        FinanceStockAlertTriggerType triggerType,
        BigDecimal thresholdValue,
        BigDecimal observedPrice,
        BigDecimal observedChangePercent,
        FinanceAlertDeliveryChannel channel,
        FinanceAlertDeliveryStatus status,
        String message,
        String providerResponse,
        Instant createdAt) {}
