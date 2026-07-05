package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.rh-crypto-tracker")
public record RobinhoodRhCryptoTrackerProperties(
        String snapshotCron,
        String snapshotSchedulerEnabledConfig) {

    public RobinhoodRhCryptoTrackerProperties {
        if (snapshotCron == null) {
            snapshotCron = "";
        } else {
            snapshotCron = snapshotCron.trim();
        }
        if (snapshotSchedulerEnabledConfig == null) {
            snapshotSchedulerEnabledConfig = "true";
        } else {
            snapshotSchedulerEnabledConfig = snapshotSchedulerEnabledConfig.trim();
        }
    }

    public boolean snapshotCronEnabled() {
        return !snapshotCron.isBlank();
    }

    public boolean snapshotSchedulerActive() {
        return schedulerEnabledByConfig() && snapshotCronEnabled();
    }

    private boolean schedulerEnabledByConfig() {
        String v = snapshotSchedulerEnabledConfig;
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }
}
