package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodAgenticSettingsRequestDto(
        Boolean requireApproval,
        BigDecimal maxOrderNotional,
        String allowedSymbols,
        Boolean autoTradeEnabled,
        Boolean autoTradeKillSwitch,
        Boolean autoTradeRequireApproval,
        BigDecimal autoTradeMinPositivityBuy,
        BigDecimal autoTradeMaxPositivitySell,
        BigDecimal autoTradeMinSpikeZ,
        Integer autoTradeMinMentions24h,
        BigDecimal autoTradeOrderQuantity,
        Integer autoTradeMaxTradesPerDay,
        BigDecimal autoTradeMaxDailyNotional,
        Integer autoTradeCooldownMinutes,
        Boolean autoTradeMarketHoursOnly) {}
