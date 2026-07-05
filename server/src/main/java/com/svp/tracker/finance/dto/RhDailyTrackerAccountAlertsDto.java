package com.svp.tracker.finance.dto;

import java.util.List;

public record RhDailyTrackerAccountAlertsDto(
        boolean emailConfigured,
        String emailHint,
        List<RhDailyTrackerAccountAlertDto> accounts) {}
