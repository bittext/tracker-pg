package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RobinhoodIndividualMarginPeekDto(
        Long id,
        Instant capturedAt,
        LocalDate snapshotDate,
        String captureKind,
        BigDecimal cashBalance,
        BigDecimal equityMarketValue,
        BigDecimal portfolioValue,
        BigDecimal optionsValue,
        BigDecimal buyingPower,
        BigDecimal unleveragedBuyingPower,
        BigDecimal marginDebit,
        BigDecimal borrowPercent,
        BigDecimal annualRatePercent,
        BigDecimal dailyInterest,
        BigDecimal maintenanceRequirement,
        String maintenanceSource,
        BigDecimal bufferAmount,
        BigDecimal bufferPercent,
        boolean nearCall,
        boolean highBorrow,
        String riskStatus,
        String riskLabel,
        BigDecimal debitDelta,
        BigDecimal borrowDelta) {}
