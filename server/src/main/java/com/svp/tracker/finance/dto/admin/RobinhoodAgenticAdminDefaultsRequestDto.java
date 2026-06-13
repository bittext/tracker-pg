package com.svp.tracker.finance.dto.admin;

public record RobinhoodAgenticAdminDefaultsRequestDto(
        Boolean requireApproval,
        java.math.BigDecimal maxOrderNotional,
        String allowedSymbols,
        Boolean autoTradeEnabled,
        Boolean autoTradeKillSwitch,
        Boolean autoTradeRequireApproval,
        java.math.BigDecimal autoTradeMinPositivityBuy,
        java.math.BigDecimal autoTradeMaxPositivitySell,
        java.math.BigDecimal autoTradeMinSpikeZ,
        Integer autoTradeMinMentions24h,
        java.math.BigDecimal autoTradeOrderQuantity,
        Integer autoTradeMaxTradesPerDay,
        java.math.BigDecimal autoTradeMaxDailyNotional,
        Integer autoTradeCooldownMinutes,
        Boolean autoTradeMarketHoursOnly,
        Boolean approvalAlertEmailEnabled,
        Boolean approvalAlertSmsEnabled) {}
