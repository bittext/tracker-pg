package com.svp.tracker.finance.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

/** Editable admin default guardrails applied to new users and via "Apply defaults". */
public record RobinhoodAgenticAdminDefaultsDto(
        boolean requireApproval,
        BigDecimal maxOrderNotional,
        String allowedSymbols,
        boolean autoTradeEnabled,
        boolean autoTradeKillSwitch,
        boolean autoTradeRequireApproval,
        BigDecimal autoTradeMinPositivityBuy,
        BigDecimal autoTradeMaxPositivitySell,
        BigDecimal autoTradeMinSpikeZ,
        int autoTradeMinMentions24h,
        BigDecimal autoTradeOrderQuantity,
        int autoTradeMaxTradesPerDay,
        BigDecimal autoTradeMaxDailyNotional,
        int autoTradeCooldownMinutes,
        boolean autoTradeMarketHoursOnly,
        boolean approvalAlertEmailEnabled,
        boolean approvalAlertSmsEnabled,
        Instant updatedAt) {}
