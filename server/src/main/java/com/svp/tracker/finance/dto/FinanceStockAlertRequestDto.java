package com.svp.tracker.finance.dto;

import com.svp.tracker.finance.domain.FinanceStockAlertRepeatMode;
import com.svp.tracker.finance.domain.FinanceStockAlertTriggerType;
import java.math.BigDecimal;

public record FinanceStockAlertRequestDto(
        String symbol,
        FinanceStockAlertTriggerType triggerType,
        BigDecimal thresholdValue,
        FinanceStockAlertRepeatMode repeatMode,
        Integer cooldownMinutes,
        Boolean enabled) {}
