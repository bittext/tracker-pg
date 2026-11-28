package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

public record RobinhoodQuarterDto(
        String key,
        String label,
        LocalDate start,
        LocalDate end,
        /** True while the quarter is still running. */
        boolean partial,
        RobinhoodStatementDto combined,
        List<RobinhoodStatementDto> accounts) {}
