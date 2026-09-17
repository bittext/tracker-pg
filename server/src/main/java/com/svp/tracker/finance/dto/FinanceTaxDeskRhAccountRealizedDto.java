package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record FinanceTaxDeskRhAccountRealizedDto(
        String suffix, String label, BigDecimal realized, int closingTrades) {}
