package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FinanceTaxDeskTodayDto(
        LocalDate tradeDate,
        int tradeCount,
        int sellCount,
        BigDecimal realizedToday,
        BigDecimal buyNotional,
        BigDecimal sellNotional,
        List<FinanceTaxDeskTradeRowDto> trades) {}

