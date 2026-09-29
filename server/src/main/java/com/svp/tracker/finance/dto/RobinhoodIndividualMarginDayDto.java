package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodIndividualMarginDayDto(
        LocalDate date,
        RobinhoodIndividualMarginPeekDto close,
        BigDecimal debitChange,
        BigDecimal borrowChange,
        List<RobinhoodIndividualMarginPeekDto> peeks) {}
