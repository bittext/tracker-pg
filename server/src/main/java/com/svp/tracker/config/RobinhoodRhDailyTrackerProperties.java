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
        int snapshotClosingHour,
        String snapshotSchedulerEnabledConfig,
        List<String> excludedAccountSuffixes,
        Map<String, String> additionalOwnerSuffixes,
        boolean alertsEnabled,
        Ai ai) {

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
        if (snapshotClosingHour < 0 || snapshotClosingHour > 23) {
            snapshotClosingHour = 21;
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
        if (ai == null) {
            ai = new Ai(false, "", "https://api.openai.com/v1", "gpt-4o-mini", 60_000, 1200);
        }
    }

    /**
     * OpenAI-backed Daily Tracker habit insights.
     *
     * @param enabled master switch (also requires a non-blank apiKey to be usable)
     * @param apiKey OpenAI API key (never logged)
     * @param baseUrl API root, default https://api.openai.com/v1
     * @param model chat model id
     * @param timeoutMs HTTP timeout
     * @param maxOutputTokens completion cap
     */
    public record Ai(
            boolean enabled,
            String apiKey,
            String baseUrl,
            String model,
            int timeoutMs,
            int maxOutputTokens) {

        public Ai {
            if (apiKey == null) {
                apiKey = "";
            } else {
                apiKey = apiKey.trim();
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.openai.com/v1";
            } else {
                baseUrl = baseUrl.trim().replaceAll("/+$", "");
            }
            if (model == null || model.isBlank()) {
                model = "gpt-4o-mini";
            } else {
                model = model.trim();
            }
            if (timeoutMs <= 0) {
                timeoutMs = 60_000;
            }
            if (maxOutputTokens <= 0) {
                maxOutputTokens = 1200;
            }
        }

        public boolean configured() {
            return enabled && apiKey != null && !apiKey.isBlank();
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

    /** Whether the hourly auto-capture job is active. */
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

    public boolean isExcludedSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return false;
        }
        return excludedAccountSuffixes.contains(suffix.trim());
    }
}
