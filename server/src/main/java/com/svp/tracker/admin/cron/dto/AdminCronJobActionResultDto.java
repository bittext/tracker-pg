package com.svp.tracker.admin.cron.dto;

import java.time.Instant;

public record AdminCronJobActionResultDto(
        boolean ok, String jobKey, String message, Instant ranAt) {}
