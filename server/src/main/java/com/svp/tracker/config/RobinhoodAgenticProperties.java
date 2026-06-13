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
        /** Bound from sync-default-account; use {@link #syncDefaultAccount()}. */
        String syncDefaultAccountConfig) {

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
        syncDefaultAccountConfig = normalizeBooleanConfig(syncDefaultAccountConfig, false);
    }

    /** When true, sync also pulls the user's primary (default) Robinhood account. */
    public boolean syncDefaultAccount() {
        return Boolean.parseBoolean(syncDefaultAccountConfig);
    }

    private static String normalizeBooleanConfig(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return Boolean.toString(defaultValue);
        }
        String cleaned = raw.trim();
        while (!cleaned.isEmpty() && !Character.isLetterOrDigit(cleaned.charAt(cleaned.length() - 1))) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        cleaned = cleaned.toLowerCase();
        if ("true".equals(cleaned) || "1".equals(cleaned) || "yes".equals(cleaned) || "on".equals(cleaned)) {
            return "true";
        }
        if ("false".equals(cleaned) || "0".equals(cleaned) || "no".equals(cleaned) || "off".equals(cleaned)) {
            return "false";
        }
        return Boolean.toString(defaultValue);
    }

    public boolean serviceConfigured() {
        return enabled && !serviceBaseUrl.isBlank();
    }
}
