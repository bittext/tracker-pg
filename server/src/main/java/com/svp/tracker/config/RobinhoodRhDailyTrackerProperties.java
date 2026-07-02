package com.svp.tracker.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker.finance.rh-daily-tracker")
public record RobinhoodRhDailyTrackerProperties(
        String snapshotCron,
        String snapshotZone,
        String snapshotSchedulerEnabledConfig,
        List<String> excludedAccountSuffixes,
        Map<String, String> additionalOwnerSuffixes) {

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
        if (snapshotSchedulerEnabledConfig == null) {
            snapshotSchedulerEnabledConfig = "true";
        } else {
            snapshotSchedulerEnabledConfig = snapshotSchedulerEnabledConfig.trim();
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
        if (additionalOwnerSuffixes == null) {
            additionalOwnerSuffixes = Map.of();
        } else {
            additionalOwnerSuffixes = additionalOwnerSuffixes.entrySet().stream()
                    .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    e -> e.getKey().trim().toLowerCase(Locale.ROOT),
                                    e -> e.getValue() == null ? "" : e.getValue().trim()));
        }
    }

    /** Optional per-username Daily Tracker suffix allowlists for users beyond spulickal/nisha. */
    public Map<String, Set<String>> additionalOwnerSuffixesByUsername() {
        return additionalOwnerSuffixes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> parseSuffixCsv(e.getValue())));
    }

    private static Set<String> parseSuffixCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean snapshotCronEnabled() {
        return !snapshotCron.isBlank();
    }

    /** Whether the daily 9 PM auto-capture job is active. */
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
        return "Daily at 9:00 PM " + snapshotZone;
    }

    public boolean isExcludedSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return false;
        }
        return excludedAccountSuffixes.contains(suffix.trim());
    }
}
