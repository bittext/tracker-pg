package com.svp.tracker.admin.cron;

import com.svp.tracker.admin.cron.domain.AdminCronJob;
import com.svp.tracker.admin.cron.repository.AdminCronJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminCronJobSchedulerManager {

    private final TaskScheduler taskScheduler;
    private final AdminCronJobRepository jobRepository;
    private final AdminCronJobRunnerRegistry runnerRegistry;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public AdminCronJobSchedulerManager(
            @Qualifier("adminCronTaskScheduler") TaskScheduler taskScheduler,
            AdminCronJobRepository jobRepository,
            AdminCronJobRunnerRegistry runnerRegistry) {
        this.taskScheduler = taskScheduler;
        this.jobRepository = jobRepository;
        this.runnerRegistry = runnerRegistry;
    }

    public void rescheduleAll(List<AdminCronJob> jobs) {
        futures.values().forEach(f -> f.cancel(false));
        futures.clear();
        for (AdminCronJob job : jobs) {
            scheduleJob(job);
        }
    }

    public void rescheduleJob(AdminCronJob job) {
        ScheduledFuture<?> existing = futures.remove(job.getJobKey());
        if (existing != null) {
            existing.cancel(false);
        }
        scheduleJob(job);
    }

    public void enqueueRunNow(AdminCronJob job) {
        taskScheduler.schedule(() -> executeJob(job.getJobKey(), job.getRunnerKey()), Instant.now());
    }

    private void scheduleJob(AdminCronJob job) {
        if (!job.isEnabled()) {
            job.setNextRunAt(null);
            return;
        }
        Optional<AdminCronJobRunnerDefinition> runner = runnerRegistry.find(job.getRunnerKey());
        if (runner.isEmpty() || !runner.get().isAvailable()) {
            log.warn(
                    "Cron job {} skipped — runner {} unavailable",
                    job.getJobKey(),
                    job.getRunnerKey());
            job.setNextRunAt(null);
            return;
        }
        Runnable task = () -> executeJob(job.getJobKey(), job.getRunnerKey());
        try {
            ScheduledFuture<?> future =
                    switch (job.getScheduleType()) {
                        case "CRON" -> taskScheduler.schedule(
                                task,
                                new CronTrigger(
                                        job.getCronExpression().trim(),
                                        ZoneId.of(job.getZoneId())));
                        case "FIXED_DELAY" -> taskScheduler.scheduleWithFixedDelay(
                                task,
                                Instant.now().plusMillis(Math.max(0L, job.getInitialDelayMs())),
                                Duration.ofMillis(Math.max(1L, job.getFixedDelayMs())));
                        default -> throw new IllegalStateException("Unknown schedule type: " + job.getScheduleType());
                    };
            futures.put(job.getJobKey(), future);
            job.setNextRunAt(AdminCronJobScheduleSupport.nextRunAt(job));
        } catch (Exception e) {
            log.error("Failed to schedule cron job {}: {}", job.getJobKey(), e.getMessage());
            job.setNextRunAt(null);
        }
    }

    private void executeJob(String jobKey, String runnerKey) {
        Instant started = Instant.now();
        try {
            runnerRegistry.run(runnerKey);
            jobRepository
                    .findById(jobKey)
                    .ifPresent(job -> {
                        job.setLastRunAt(started);
                        job.setLastRunStatus("OK");
                        job.setLastRunMessage("Completed at " + started);
                        job.setNextRunAt(AdminCronJobScheduleSupport.nextRunAt(job));
                        jobRepository.save(job);
                    });
        } catch (Exception e) {
            log.warn("Cron job {} failed: {}", jobKey, e.getMessage());
            jobRepository
                    .findById(jobKey)
                    .ifPresent(job -> {
                        job.setLastRunAt(started);
                        job.setLastRunStatus("ERROR");
                        job.setLastRunMessage(truncate(e.getMessage(), 500));
                        job.setNextRunAt(AdminCronJobScheduleSupport.nextRunAt(job));
                        jobRepository.save(job);
                    });
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
