package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodYtdCheckCloseDto(
        String timestamp, String symbol, String side, String quantity, BigDecimal price, BigDecimal realized) {}
