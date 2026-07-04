package com.svp.tracker.admin.cron.dto;

import java.time.Instant;

public record AdminCronJobDto(
        String jobKey,
        String displayName,
        String description,
        String category,
        String scheduleType,
        String cronExpression,
        Long fixedDelayMs,
        Long initialDelayMs,
        String zoneId,
        boolean enabled,
        boolean builtIn,
        String runnerKey,
        String runnerLabel,
        Instant lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        Instant nextRunAt,
        String scheduleSummary,
        boolean runnerAvailable) {}
