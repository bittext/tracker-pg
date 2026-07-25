package com.svp.tracker.finance.dto;

import java.util.List;

/** Multi-scenario overlay payload for the Then & now compare chart. */
public record InvestmentThenNowOverlayResponseDto(
        List<InvestmentThenNowOverlaySeriesDto> series, List<String> warnings) {}
