package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodCashIoLedgerDto(
        int year,
        Integer month,
        String accountSuffix,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal net,
        List<RobinhoodCashIoEntryDto> entries) {}
