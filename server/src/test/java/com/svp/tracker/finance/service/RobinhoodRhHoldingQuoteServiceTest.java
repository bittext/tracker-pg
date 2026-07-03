package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhLiveQuotesDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobinhoodRhHoldingQuoteServiceTest {

    @Test
    void optionInstrumentIdUsesInstrumentSegmentForThreePartPositionKey() {
        assertEquals(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                RobinhoodRhHoldingQuoteService.optionInstrumentId(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|long"));
    }

    @Test
    void lookupOptionInstrumentIdMatchesPerShareAverageAfterNormalization() {
        RobinhoodAgenticPosition position = new RobinhoodAgenticPosition();
        position.setPositionType("option");
        position.setSymbol("NBIS");
        position.setQuantity(new BigDecimal("1"));
        position.setAverageBuyPrice(new BigDecimal("1000"));
        position.setPositionKey("instrument-abc|call");

        Map<String, String> byKey = RobinhoodRhHoldingQuoteService.instrumentIdsByMatchKey(List.of(position));

        RobinhoodRhHoldingDto holding = new RobinhoodRhHoldingDto(
                "NBIS",
                "option",
                new BigDecimal("1"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertEquals("instrument-abc", RobinhoodRhHoldingQuoteService.lookupOptionInstrumentId(holding, byKey));
    }

    @Test
    void applyLiveQuoteUsesPerShareAverageMatchKey() {
        RobinhoodAgenticPosition position = new RobinhoodAgenticPosition();
        position.setPositionType("option");
        position.setSymbol("NBIS");
        position.setQuantity(new BigDecimal("1"));
        position.setAverageBuyPrice(new BigDecimal("1000"));
        position.setPositionKey("instrument-abc|call");

        Map<String, String> optionIds = RobinhoodRhHoldingQuoteService.instrumentIdsByMatchKey(List.of(position));
        RobinhoodRhLiveQuotesDto quotes = new RobinhoodRhLiveQuotesDto(
                Map.of(), Map.of("instrument-abc", new BigDecimal("10.68")));

        RobinhoodRhHoldingDto raw = new RobinhoodRhHoldingDto(
                "NBIS",
                "option",
                new BigDecimal("1"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        List<RobinhoodRhHoldingDto> finalized = RobinhoodRhHoldingValues.finalizeHoldings(
                List.of(raw), BigDecimal.ZERO, quotes, optionIds);
        RobinhoodRhHoldingDto out = finalized.get(0);

        assertEquals(new BigDecimal("10.6800"), out.currentUnitPrice());
        assertEquals(new BigDecimal("1068.00"), out.marketValue());
        assertNotNull(out.unrealizedPnL());
    }
}
