package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketsRoadmapSlapCrossingDto(
        BigDecimal threshold,
        LocalDate crossedOn,
        BigDecimal totalOnDay,
        BigDecimal priorTotal) {}
