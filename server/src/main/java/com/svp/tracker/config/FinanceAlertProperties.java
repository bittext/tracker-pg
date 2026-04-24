package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.alerts")
public record FinanceAlertProperties(
        boolean evaluationEnabled,
        long pollFixedDelayMs,
        int defaultCooldownMinutes,
        int maxEventsReturned,
        String emailFrom,
        boolean emailEnabled,
        boolean smsEnabled,
        String twilioAccountSid,
        String twilioAuthToken,
        String twilioFromNumber) {

    public FinanceAlertProperties {
        if (pollFixedDelayMs < 30_000) {
            pollFixedDelayMs = 300_000;
        }
        if (defaultCooldownMinutes < 0) {
            defaultCooldownMinutes = 1440;
        }
        if (maxEventsReturned < 1) {
            maxEventsReturned = 50;
        }
        if (maxEventsReturned > 500) {
            maxEventsReturned = 500;
        }
        emailFrom = clean(emailFrom);
        twilioAccountSid = clean(twilioAccountSid);
        twilioAuthToken = clean(twilioAuthToken);
        twilioFromNumber = clean(twilioFromNumber);
    }

    public boolean emailProviderConfigured() {
        return emailEnabled && !emailFrom.isBlank();
    }

    public boolean smsProviderConfigured() {
        return smsEnabled && !twilioAccountSid.isBlank() && !twilioAuthToken.isBlank() && !twilioFromNumber.isBlank();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
