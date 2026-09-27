package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodIndividualMarginStandingDto(
        String headline,
        String riskStatus,
        String riskLabel,
        String tone,
        BigDecimal borrowPercent,
        BigDecimal bufferPercent,
        BigDecimal maintenanceSharePercent,
        BigDecimal interestMonthEstimate,
        String maintenanceNote) {}
