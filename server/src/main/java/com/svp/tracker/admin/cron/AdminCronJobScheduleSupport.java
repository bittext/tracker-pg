package com.svp.tracker.admin.cron;

import com.svp.tracker.admin.cron.domain.AdminCronJob;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

final class AdminCronJobScheduleSupport {

    private AdminCronJobScheduleSupport() {}

    static Instant nextRunAt(AdminCronJob job) {
        if (!job.isEnabled()) {
            return null;
        }
        if ("CRON".equals(job.getScheduleType())) {
            try {
                CronExpression expression = CronExpression.parse(job.getCronExpression().trim());
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
