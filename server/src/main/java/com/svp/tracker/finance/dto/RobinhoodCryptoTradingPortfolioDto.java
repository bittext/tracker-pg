package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record RobinhoodCryptoTradingPortfolioDto(
        String accountNumber, BigDecimal totalValue, List<RobinhoodRhCryptoHoldingDto> holdings) {}
