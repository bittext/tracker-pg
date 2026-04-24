package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;

public record FinanceAlertEvaluationDto(
        Instant evaluatedAt,
        int checkedAlerts,
        int triggeredAlerts,
        List<FinanceAlertEventDto> events) {}
