package com.svp.tracker.finance.dto;

import java.util.List;

/** GET /api/finance/robinhood/quarterly-statements */
public record RobinhoodQuarterlyStatementsDto(
        int year,
        List<RobinhoodRhPeriodAccountColumnDto> accounts,
        List<RobinhoodQuarterDto> quarters,
        List<String> notes) {}
