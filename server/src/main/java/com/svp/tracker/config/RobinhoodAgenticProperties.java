package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.robinhood-agentic")
public record RobinhoodAgenticProperties(
        boolean enabled,
        String serviceBaseUrl,
        int serviceTimeoutMs,
        /** AES-256-GCM sealing for OAuth tokens at rest (passphrase or Base64 32-byte key). */
        String tokenEncryptionKey,
        /** Cron for scheduled sync (empty disables scheduler). */
        String syncCron,
        boolean syncDefaultAccount) {

    public RobinhoodAgenticProperties {
        if (serviceBaseUrl == null) {
            serviceBaseUrl = "";
        } else {
            serviceBaseUrl = serviceBaseUrl.trim();
        }
        if (serviceTimeoutMs < 5_000) {
            serviceTimeoutMs = 60_000;
        }
        if (serviceTimeoutMs > 300_000) {
            serviceTimeoutMs = 300_000;
        }
        if (tokenEncryptionKey == null) {
            tokenEncryptionKey = "";
        } else {
            tokenEncryptionKey = tokenEncryptionKey.trim();
        }
        if (syncCron == null) {
            syncCron = "";
        } else {
            syncCron = syncCron.trim();
        }
    }

    public boolean serviceConfigured() {
        return enabled && !serviceBaseUrl.isBlank();
    }
}
