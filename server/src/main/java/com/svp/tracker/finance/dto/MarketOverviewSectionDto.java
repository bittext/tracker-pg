package com.svp.tracker.finance.dto;

import java.util.List;

public record MarketOverviewSectionDto(String title, String subtitle, List<MarketOverviewInstrumentDto> rows) {}
