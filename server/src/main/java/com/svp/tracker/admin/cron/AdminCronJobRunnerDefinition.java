package com.svp.tracker.admin.cron;

import java.util.function.BooleanSupplier;

/** Metadata for a runnable scheduled job. */
public record AdminCronJobRunnerDefinition(
        String runnerKey,
        String label,
        String description,
        String category,
        Runnable action,
        BooleanSupplier available) {

    public AdminCronJobRunnerDefinition(
            String runnerKey, String label, String description, String category, Runnable action) {
        this(runnerKey, label, description, category, action, () -> true);
    }

    public boolean isAvailable() {
        return available == null || available.getAsBoolean();
    }
}
