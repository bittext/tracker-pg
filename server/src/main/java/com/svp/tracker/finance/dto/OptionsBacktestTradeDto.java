package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OptionsBacktestTradeDto(
        LocalDate openDate,
        LocalDate closeDate,
        String action,
        String optionType,
        BigDecimal strike,
        BigDecimal underlyingOpen,
        BigDecimal underlyingClose,
        BigDecimal premiumPerShare,
        BigDecimal pnl,
        String outcome,
        BigDecimal equityAfter) {}
