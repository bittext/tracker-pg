package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record RobinhoodRhCryptoTrackerAccountCellDto(
        String accountSuffix,
        String label,
        BigDecimal totalValue,
        BigDecimal changeFromPrevious,
        List<RobinhoodRhCryptoHoldingDto> holdings) {}
