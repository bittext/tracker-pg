package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodAgenticSettingsRequestDto(
        Boolean requireApproval, BigDecimal maxOrderNotional, String allowedSymbols) {}
