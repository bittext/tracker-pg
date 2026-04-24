package com.svp.tracker.finance.dto;

import java.time.Instant;

public record FinanceNotificationSettingsDto(
        Long id,
        String emailAddress,
        String mobileE164,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean emailProviderConfigured,
        boolean smsProviderConfigured,
        Instant updatedAt) {}
