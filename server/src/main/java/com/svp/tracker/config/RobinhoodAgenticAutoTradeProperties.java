package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.robinhood-agentic.auto-trade")
public record RobinhoodAgenticAutoTradeProperties(
        /** Server master switch for scheduled AI auto-trade evaluation. */
        String enabledConfig,
        long pollFixedDelayMs,
        long pollInitialDelayMs) {

    public RobinhoodAgenticAutoTradeProperties {
        enabledConfig = normalizeBooleanConfig(enabledConfig, false);
        if (pollFixedDelayMs < 60_000L) {
            pollFixedDelayMs = 300_000L;
        }
        if (pollInitialDelayMs < 0L) {
            pollInitialDelayMs = 120_000L;
        }
    }

    public boolean enabled() {
        return Boolean.parseBoolean(enabledConfig);
    }

    private static String normalizeBooleanConfig(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return Boolean.toString(defaultValue);
        }
        String cleaned = raw.trim().toLowerCase();
        return switch (cleaned) {
            case "true", "1", "yes", "on" -> "true";
            case "false", "0", "no", "off" -> "false";
            default -> Boolean.toString(defaultValue);
        };
    }
}
