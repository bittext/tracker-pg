package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodRhDailyTrackerReportDto(
        int year,
        Integer month,
        BigDecimal monthCombinedTotal,
        BigDecimal monthCombinedChange,
        BigDecimal yearCombinedTotal,
        BigDecimal yearCombinedChange,
        boolean autoCaptureScheduled,
        String autoCaptureScheduleLabel,
        List<RobinhoodRhDailyTrackerAccountColumnDto> accounts,
        List<RobinhoodRhDailyTrackerDayDto> days,
        List<String> notes) {}
