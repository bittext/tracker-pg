package com.svp.tracker.admin.cron.dto;

public record AdminCronJobUpsertRequestDto(
        String displayName,
        String description,
        String category,
        String scheduleType,
        String cronExpression,
        Long fixedDelayMs,
        Long initialDelayMs,
        String zoneId,
        Boolean enabled,
        String runnerKey) {}
