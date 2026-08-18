package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodCashIoDailyDto(
        LocalDate asOfDate,
        BigDecimal dayInputs,
        BigDecimal dayOutputs,
        BigDecimal dayCredits,
        BigDecimal dayDebits,
        BigDecimal ytdInputs,
        BigDecimal ytdOutputs,
        BigDecimal ytdCredits,
        BigDecimal ytdDebits,
        BigDecimal adjustedNow,
        BigDecimal liveValue,
        List<RobinhoodCashIoLiveAccountDto> liveAccounts,
        Instant capturedAt) {}
