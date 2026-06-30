package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.webull-quote")
public record WebullQuoteProperties(boolean enabled, String serviceBaseUrl, int serviceTimeoutMs) {

    public WebullQuoteProperties {
        if (serviceBaseUrl == null) {
            serviceBaseUrl = "";
        } else {
            serviceBaseUrl = serviceBaseUrl.trim();
        }
        if (serviceTimeoutMs < 5_000) {
            serviceTimeoutMs = 30_000;
        }
        if (serviceTimeoutMs > 120_000) {
            serviceTimeoutMs = 120_000;
        }
    }

    public boolean serviceConfigured() {
        return enabled && !serviceBaseUrl.isBlank();
    }
}
