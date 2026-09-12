package com.svp.tracker.finance.dto;

import java.util.List;

/**
 * Month-start and month-end (and year) balances from Daily Tracker scheduled closes.
 * Opening is the last 9 PM CT close before the period, or the first close in the period when
 * tracking started later. Closing is the last close on or before period end. Open windows
 * (day / week / month / YTD) use the latest hourly capture as the ending print.
 */
public record RobinhoodRhPeriodBalancesDto(
        int year,
        String note,
        List<RobinhoodRhPeriodAccountColumnDto> accounts,
        List<RobinhoodRhPeriodBalanceRowDto> windows,
        List<RobinhoodRhPeriodBalanceRowDto> months,
        RobinhoodRhPeriodBalanceRowDto yearBalance) {}
