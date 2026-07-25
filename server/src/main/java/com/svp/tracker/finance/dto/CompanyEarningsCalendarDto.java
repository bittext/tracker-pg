package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

public record CompanyEarningsCalendarDto(
        LocalDate from,
        LocalDate to,
        int dayCount,
        List<CompanyEarningsEventDto> events,
        String source) {}
