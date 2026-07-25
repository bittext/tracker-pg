package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto.ForwardPoint;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto.InvestmentThenNowOutlookSymbolDto;
import com.svp.tracker.finance.dto.InvestmentThenNowOutlookDto.ScenarioBand;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentThenNowOutlookSerializeTest {

    @Test
    void serializesInstantAndLocalDate() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var band = new ScenarioBand("n", new BigDecimal("200.0000"), "50%");
        var pt = new ForwardPoint(LocalDate.of(2026, 8, 1), new BigDecimal("210.0000"));
        var symbol = new InvestmentThenNowOutlookSymbolDto(
                "NVDA",
                "NVIDIA",
                17L,
                "thesis",
                band,
                band,
                band,
                List.of("c"),
                List.of("r"),
                List.of(pt),
                List.of(pt),
                List.of(pt));
        var dto = new InvestmentThenNowOutlookDto(
                "disclaimer", 6, "summary", "gpt", Instant.parse("2026-07-25T21:00:00Z"), false, List.of(symbol));
        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("2026-08-01"));
        assertTrue(json.contains("2026-07-25T21:00:00Z"));
        InvestmentThenNowOutlookDto round = mapper.readValue(json, InvestmentThenNowOutlookDto.class);
        assertTrue(round.symbols().get(0).forwardBase().get(0).date().equals(LocalDate.of(2026, 8, 1)));
    }
}
