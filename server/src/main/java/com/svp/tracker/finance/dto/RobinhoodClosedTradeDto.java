package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One FIFO-matched close (partial or full lot). */
public record RobinhoodClosedTradeDto(
        String instrument,
        String contract,
        String strategy,
        LocalDate buyDate,
        LocalDate sellDate,
        int holdDays,
        BigDecimal quantity,
        BigDecimal realizedPnL) {}
