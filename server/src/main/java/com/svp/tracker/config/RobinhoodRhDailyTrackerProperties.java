package com.svp.tracker.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.rh-daily-tracker")
public record RobinhoodRhDailyTrackerProperties(
        String snapshotCron, String snapshotZone, List<String> excludedAccountSuffixes) {

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
        if (excludedAccountSuffixes == null) {
            excludedAccountSuffixes = List.of();
        } else {
            excludedAccountSuffixes = excludedAccountSuffixes.stream()
                    .map(s -> s == null ? "" : s.trim())
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
    }

    public boolean snapshotCronEnabled() {
        return !snapshotCron.isBlank();
    }

    public boolean isExcludedSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return false;
        }
        return excludedAccountSuffixes.contains(suffix.trim());
    }
}
