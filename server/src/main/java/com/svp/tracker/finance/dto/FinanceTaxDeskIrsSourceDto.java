package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceTaxDeskIrsSourceDto(
        String kind, String title, LocalDate date, BigDecimal amount, String notes, String sourceRef) {}
