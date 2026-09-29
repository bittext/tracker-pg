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
        /** One close peek per past day; today’s full peek list is on {@code days}. */
        List<RobinhoodIndividualMarginPeekDto> ledger,
        List<RobinhoodIndividualMarginAlertEventDto> alerts,
        boolean emailConfigured,
        String emailHint,
        List<String> notes) {}
