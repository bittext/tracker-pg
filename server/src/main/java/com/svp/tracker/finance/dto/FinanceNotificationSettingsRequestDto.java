package com.svp.tracker.finance.dto;

public record FinanceNotificationSettingsRequestDto(
        String emailAddress,
        String mobileE164,
        Boolean emailEnabled,
        Boolean smsEnabled) {}
