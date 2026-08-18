package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodCashIoLiveAccountDto(String suffix, String label, BigDecimal value) {}
