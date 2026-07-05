package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.rh-crypto-auto-trade")
public record RobinhoodRhCryptoAutoTradeProperties(
        String enabledConfig, String pollCron) {

    public RobinhoodRhCryptoAutoTradeProperties {
        if (enabledConfig == null) {
            enabledConfig = "false";
        } else {
            enabledConfig = enabledConfig.trim();
        }
        if (pollCron == null) {
            pollCron = "";
        } else {
            pollCron = pollCron.trim();
        }
    }

    public boolean enabled() {
        String v = enabledConfig;
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    public boolean pollCronEnabled() {
        return !pollCron.isBlank();
    }

    public boolean schedulerActive() {
        return enabled() && pollCronEnabled();
    }
}
