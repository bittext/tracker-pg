package com.svp.tracker.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.auth.sms")
public record SmsProperties(
        boolean enabled,
        String provider,
        String twilioAccountSid,
        String twilioAuthToken,
        String twilioFromNumber) {

    public SmsProperties {
        if (provider == null || provider.isBlank()) {
            provider = "log";
        }
        if (twilioAccountSid == null) {
            twilioAccountSid = "";
        }
        if (twilioAuthToken == null) {
            twilioAuthToken = "";
        }
        if (twilioFromNumber == null) {
            twilioFromNumber = "";
        }
    }
}
