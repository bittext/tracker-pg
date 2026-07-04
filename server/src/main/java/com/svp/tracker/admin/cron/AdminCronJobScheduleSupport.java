package com.svp.tracker.admin.cron;

import com.svp.tracker.admin.cron.domain.AdminCronJob;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

final class AdminCronJobScheduleSupport {

    private AdminCronJobScheduleSupport() {}

    /**
     * Accepts Unix 5-field ({@code min hour dom month dow}) or Spring 6-field ({@code sec min hour dom month dow})
     * cron and returns a validated 6-field expression for {@link CronExpression}.
     */
    static String normalizeCronExpression(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("cron expression is required");
        }
        String trimmed = raw.trim();
        String[] fields = trimmed.split("\\s+");
        String sixField =
                switch (fields.length) {
                    case 5 -> "0 " + trimmed;
                    case 6 -> trimmed;
                    default -> throw new IllegalArgumentException(
                            "Cron expression must have 5 fields (min hour dom month dow) or 6 fields "
                                    + "(sec min hour dom month dow); found "
                                    + fields.length
                                    + " in \""
                                    + trimmed
                                    + "\"");
                };
        CronExpression.parse(sixField);
        return sixField;
    }

    static Instant nextRunAt(AdminCronJob job) {
        if (!job.isEnabled()) {
            return null;
        }
        if ("CRON".equals(job.getScheduleType())) {
            try {
                CronExpression expression = CronExpression.parse(normalizeCronExpression(job.getCronExpression()));
                ZoneId zone = ZoneId.of(job.getZoneId());
                ZonedDateTime next = expression.next(ZonedDateTime.now(zone));
                return next == null ? null : next.toInstant();
            } catch (Exception ignored) {
                return null;
            }
        }
        if ("FIXED_DELAY".equals(job.getScheduleType())) {
            Instant anchor = job.getLastRunAt() != null ? job.getLastRunAt() : Instant.now();
            long delay = job.getFixedDelayMs() == null ? 0L : job.getFixedDelayMs();
            long initial = job.getInitialDelayMs();
            if (job.getLastRunAt() == null && initial > 0) {
                return Instant.now().plusMillis(initial);
            }
            return anchor.plusMillis(Math.max(1L, delay));
        }
        return null;
    }

    static String scheduleSummary(AdminCronJob job) {
        if ("CRON".equals(job.getScheduleType())) {
            return "Cron " + job.getCronExpression().trim() + " (" + job.getZoneId() + ")";
        }
        if ("FIXED_DELAY".equals(job.getScheduleType())) {
            long delayMs = job.getFixedDelayMs() == null ? 0L : job.getFixedDelayMs();
            long minutes = Math.max(1L, delayMs / 60_000L);
            if (job.getInitialDelayMs() > 0) {
                return "Every ~" + minutes + " min after prior run (initial delay "
                        + (job.getInitialDelayMs() / 1000L)
                        + "s)";
            }
            return "Every ~" + minutes + " min after prior run completes";
        }
        return "—";
    }
}
