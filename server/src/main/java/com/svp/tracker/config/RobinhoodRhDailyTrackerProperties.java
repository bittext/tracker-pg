package com.svp.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.rh-daily-tracker")
public record RobinhoodRhDailyTrackerProperties(String snapshotCron, String snapshotZone) {

    public RobinhoodRhDailyTrackerProperties {
        if (snapshotCron == null) {
            snapshotCron = "";
        } else {
            snapshotCron = snapshotCron.trim();
        }
        if (snapshotZone == null || snapshotZone.isBlank()) {
            snapshotZone = "America/Chicago";
        } else {
            snapshotZone = snapshotZone.trim();
        }
    }

    public boolean snapshotCronEnabled() {
        return !snapshotCron.isBlank();
    }
}
