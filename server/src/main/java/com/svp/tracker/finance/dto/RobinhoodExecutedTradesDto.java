package com.svp.tracker.finance.dto;

import java.util.List;

/** All executed Robinhood buys and sells for a calendar year (America/Chicago). */
public record RobinhoodExecutedTradesDto(
        int year,
        String note,
        List<RobinhoodRhPeriodAccountColumnDto> accounts,
        List<String> tradedSymbols,
        List<RobinhoodExecutedTradeDto> trades) {}
