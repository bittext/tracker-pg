package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketsJourneyLiveSeriesPointDto(
        LocalDate date, BigDecimal total, BigDecimal dayChange, BigDecimal dayChangePct) {}
