package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticSettingsDto(
        boolean executionEnabled,
        boolean autoTradeServerEnabled,
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
        Instant autoTradeLastRunAt,
        String autoTradeLastRunMessage,
        Instant updatedAt) {}
