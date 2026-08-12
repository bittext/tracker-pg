package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

public record RobinhoodSelectiveTradeLedgerDto(
        int year,
        Integer month,
        LocalDate fromDate,
        LocalDate toDate,
        RobinhoodSelectiveTradeStatsDto stats,
        List<RobinhoodSelectiveTradeEntryDto> entries) {}
