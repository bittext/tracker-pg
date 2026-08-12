package com.svp.tracker.finance.dto;

import java.time.LocalDate;

public record RobinhoodSelectiveTradeRequestDto(
        LocalDate activityDate, String symbol, String outcome, String note, String accountSuffix) {}
