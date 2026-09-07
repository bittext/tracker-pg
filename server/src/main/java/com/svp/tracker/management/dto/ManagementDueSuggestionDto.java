package com.svp.tracker.management.dto;

import java.math.BigDecimal;

public record ManagementDueSuggestionDto(
        String side, String counterparty, int typicalDay, BigDecimal estimatedAmount, int sampleCount) {}
