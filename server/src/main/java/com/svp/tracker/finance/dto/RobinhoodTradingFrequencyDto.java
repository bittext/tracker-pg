package com.svp.tracker.finance.dto;

/** How often the account traded in the selected year (from FIFO closes). */
public record RobinhoodTradingFrequencyDto(
        int totalClosedLots,
        int tradingDays,
        double averageClosesPerWeek,
        double averageClosesPerMonth,
        String busiestMonthLabel,
        int busiestMonthCloses) {}
