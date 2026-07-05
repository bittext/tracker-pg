package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record RobinhoodCryptoTradingSyncResultDto(
        boolean ok,
        String message,
        String accountNumber,
        BigDecimal totalValue,
        List<RobinhoodRhCryptoHoldingDto> holdings,
        List<String> warnings) {}
