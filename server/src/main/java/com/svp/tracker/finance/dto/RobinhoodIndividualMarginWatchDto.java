package com.svp.tracker.finance.dto;

import java.util.List;

public record RobinhoodIndividualMarginWatchDto(
        String accountSuffix,
        String accountLabel,
        int year,
        RobinhoodIndividualMarginPeekDto latest,
        RobinhoodIndividualMarginStandingDto standing,
        List<RobinhoodIndividualMarginDayDto> days,
        List<RobinhoodIndividualMarginPeekDto> recentPeeks,
        /** Hourly peeks kept; only rows where a monitored figure moved. */
        List<RobinhoodIndividualMarginPeekDto> ledger,
        List<RobinhoodIndividualMarginAlertEventDto> alerts,
        boolean emailConfigured,
        String emailHint,
        List<String> notes) {}
