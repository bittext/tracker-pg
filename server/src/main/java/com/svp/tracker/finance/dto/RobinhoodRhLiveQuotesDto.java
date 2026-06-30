package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Live Robinhood marks for holdings refresh (equity per share, option mark per share). */
public record RobinhoodRhLiveQuotesDto(
        Map<String, BigDecimal> equityPriceBySymbol, Map<String, BigDecimal> optionMarkPerShareByInstrumentId) {

    public static RobinhoodRhLiveQuotesDto empty() {
        return new RobinhoodRhLiveQuotesDto(Map.of(), Map.of());
    }
}
