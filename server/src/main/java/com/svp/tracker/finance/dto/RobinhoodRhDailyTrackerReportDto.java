package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record RobinhoodRhDailyTrackerReportDto(
        int year,
        /** Single month when exactly one month is selected; null for none or multiple. */
        Integer month,
        /** Selected months (1–12); empty means all months in the year. */
        List<Integer> months,
        BigDecimal monthCombinedTotal,
        BigDecimal monthCombinedChange,
        BigDecimal yearCombinedTotal,
        BigDecimal yearCombinedChange,
        boolean autoCaptureScheduled,
        String autoCaptureScheduleLabel,
        List<RobinhoodRhDailyTrackerAccountColumnDto> accounts,
        List<RobinhoodRhDailyTrackerDayDto> days,
        List<RobinhoodRhDailyBenchmarkPointDto> sp500Benchmark,
        List<String> notes) {}
