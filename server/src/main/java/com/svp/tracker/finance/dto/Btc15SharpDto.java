package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record Btc15SharpDto(
        String label,
        String direction,
        BigDecimal lastFive,
        BigDecimal firstTen,
        boolean reversed) {}
