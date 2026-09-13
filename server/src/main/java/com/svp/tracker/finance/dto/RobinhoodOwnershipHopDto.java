package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One buy or sell hop — a frozen Daily Tracker fill, or a quantity change between snapshots
 * when the fill was not captured.
 */
public record RobinhoodOwnershipHopDto(
        Instant at,
        LocalDate date,
        String captureKind,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal fromQuantity,
        BigDecimal toQuantity,
        BigDecimal averagePrice,
        BigDecimal notional,
        /** {@code trade} from {@code trades_json}, or {@code holding} inferred from quantity. */
        String source,
        String accountSuffix,
        String accountLabel) {}
