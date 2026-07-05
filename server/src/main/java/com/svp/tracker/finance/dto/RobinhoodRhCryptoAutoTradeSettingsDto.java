package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RobinhoodRhCryptoAutoTradeSettingsDto(
        boolean serverEnabled,
        boolean connected,
        boolean autoTradeEnabled,
        boolean autoTradeKillSwitch,
        BigDecimal autoTradeMinPositivityBuy,
        BigDecimal autoTradeMaxPositivitySell,
        BigDecimal autoTradeMinSpikeZ,
        int autoTradeMinMentions24h,
        BigDecimal autoTradeOrderQuoteAmount,
        int autoTradeMaxTradesPerDay,
        BigDecimal autoTradeMaxDailyNotional,
        int autoTradeCooldownMinutes,
        List<String> allowedSymbols,
        Instant autoTradeLastRunAt,
        String autoTradeLastRunMessage) {}
