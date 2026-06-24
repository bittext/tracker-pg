package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One imported Robinhood row since the account tracker cutoff. */
public record RobinhoodAccountLedgerEventDto(
        LocalDate activityDate,
        String instrument,
        String description,
        String transCode,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        /** {@code IN}, {@code OUT}, or {@code OTHER}. */
        String direction,
        /** {@code TRADE}, {@code DIVIDEND}, {@code TRANSFER}, or {@code OTHER}. */
        String category) {}
