package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record RobinhoodRhCryptoAutoTradeSettingsRequestDto(
        Boolean autoTradeEnabled,
        Boolean autoTradeKillSwitch,
        BigDecimal autoTradeMinPositivityBuy,
        BigDecimal autoTradeMaxPositivitySell,
        BigDecimal autoTradeMinSpikeZ,
        Integer autoTradeMinMentions24h,
        BigDecimal autoTradeOrderQuoteAmount,
        Integer autoTradeMaxTradesPerDay,
        BigDecimal autoTradeMaxDailyNotional,
        Integer autoTradeCooldownMinutes,
        List<String> allowedSymbols) {}
