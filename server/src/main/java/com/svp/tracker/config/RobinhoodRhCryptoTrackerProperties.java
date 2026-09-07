package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.rh-crypto-tracker")
public record RobinhoodRhCryptoTrackerProperties(
        String snapshotCron,
        String snapshotSchedulerEnabledConfig,
        String snapshotZone,
        int snapshotClosingHour) {

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
        if (snapshotZone == null || snapshotZone.isBlank()) {
            snapshotZone = "America/Chicago";
        } else {
            snapshotZone = snapshotZone.trim();
        }
        if (snapshotClosingHour < 0 || snapshotClosingHour > 23) {
            snapshotClosingHour = 21;
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

    public String autoCaptureScheduleLabel() {
        if (!snapshotSchedulerActive()) {
            return "";
        }
        int hour12 = snapshotClosingHour % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String amPm = snapshotClosingHour < 12 ? "AM" : "PM";
        return "hourly from 12:00 AM "
                + snapshotZone
                + " (daily close "
                + hour12
                + ":00 "
                + amPm
                + ")";
    }
}
