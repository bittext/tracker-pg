package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record Btc15HourDto(
        String label,
        int settled,
        int above,
        BigDecimal aboveRate,
        List<Btc15WindowDto> windows) {}
