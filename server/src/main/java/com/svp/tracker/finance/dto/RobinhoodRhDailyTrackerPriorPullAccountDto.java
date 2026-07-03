package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhDailyTrackerPriorPullAccountDto(String accountSuffix, BigDecimal totalAccountValue) {}
