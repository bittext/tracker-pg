package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.finance.domain.FinanceAlertEvent;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.FinanceStockAlert;
import com.svp.tracker.finance.dto.FinanceAlertEventDto;
import com.svp.tracker.finance.dto.FinanceNotificationSettingsDto;
import com.svp.tracker.finance.dto.FinanceStockAlertDto;

final class FinanceAlertMapper {

    private FinanceAlertMapper() {}

    static FinanceStockAlertDto alert(FinanceStockAlert a) {
        return new FinanceStockAlertDto(
                a.getId(),
                a.getSymbol(),
                a.getCompanyName(),
                a.getTriggerType(),
                a.getThresholdValue(),
                a.getRepeatMode(),
                a.getCooldownMinutes(),
                a.isEnabled(),
                a.isTriggerArmed(),
                a.getLastCheckedAt(),
                a.getLastTriggeredAt(),
                a.getLastRegularMarketPrice(),
                a.getLastRegularMarketChangePercent(),
                a.getFireCount(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    static FinanceAlertEventDto event(FinanceAlertEvent e) {
        return new FinanceAlertEventDto(
                e.getId(),
                e.getAlertId(),
                e.getSymbol(),
                e.getTriggerType(),
                e.getThresholdValue(),
                e.getObservedPrice(),
                e.getObservedChangePercent(),
                e.getChannel(),
                e.getStatus(),
                e.getMessage(),
                e.getProviderResponse(),
                e.getCreatedAt());
    }

    static FinanceNotificationSettingsDto settings(
            FinanceNotificationSettings s, FinanceAlertProperties props) {
        return new FinanceNotificationSettingsDto(
                s.getId(),
                s.getEmailAddress(),
                s.getMobileE164(),
                s.isEmailEnabled(),
                s.isSmsEnabled(),
                props.emailProviderConfigured(),
                props.smsProviderConfigured(),
                s.getUpdatedAt());
    }
}
