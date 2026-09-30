package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodYtdCheckPositionDto(
        String symbol, BigDecimal quantity, BigDecimal averageBuyPrice, BigDecimal cost, BigDecimal unrealized) {}
