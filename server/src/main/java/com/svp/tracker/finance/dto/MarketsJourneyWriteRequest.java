package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record MarketsJourneyWriteRequest(String title, BigDecimal milestoneAmount, Integer sortOrder) {}
