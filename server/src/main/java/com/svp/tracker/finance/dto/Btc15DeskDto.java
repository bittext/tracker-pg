package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Btc15DeskDto(
        LocalDate date,
        int settled,
        int above,
        BigDecimal aboveRate,
        List<Btc15HourDto> hours) {}
