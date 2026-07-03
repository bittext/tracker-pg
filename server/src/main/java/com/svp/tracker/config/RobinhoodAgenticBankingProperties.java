package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.robinhood-agentic-banking")
public record RobinhoodAgenticBankingProperties(
        boolean enabled,
        String serviceBaseUrl,
        int serviceTimeoutMs,
        String tokenEncryptionKey) {

    public RobinhoodAgenticBankingProperties {
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
    }

    public boolean serviceConfigured() {
        return enabled && !serviceBaseUrl.isBlank() && !tokenEncryptionKey.isBlank();
    }
}
