package com.svp.tracker.finance.service.banking;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankingParsedRow(LocalDate date, BigDecimal amount, String description) {}
