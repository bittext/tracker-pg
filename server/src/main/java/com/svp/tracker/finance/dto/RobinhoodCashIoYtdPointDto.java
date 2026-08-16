package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RobinhoodCashIoYtdPointDto(LocalDate date, BigDecimal adjustedValue) {}
