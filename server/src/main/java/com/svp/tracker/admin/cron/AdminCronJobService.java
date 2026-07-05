package com.svp.tracker.admin.cron;

import com.svp.tracker.admin.cron.domain.AdminCronJob;
import com.svp.tracker.admin.cron.dto.AdminCronJobActionResultDto;
import com.svp.tracker.admin.cron.dto.AdminCronJobDto;
import com.svp.tracker.admin.cron.dto.AdminCronJobRunnerDto;
import com.svp.tracker.admin.cron.dto.AdminCronJobUpsertRequestDto;
import com.svp.tracker.admin.cron.repository.AdminCronJobRepository;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhCryptoAutoTradeProperties;
import com.svp.tracker.config.RobinhoodRhCryptoTrackerProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCronJobService {

    private final AdminCronJobRepository jobRepository;
    private final AdminCronJobRunnerRegistry runnerRegistry;
    private final AdminCronJobBuiltinCatalog builtinCatalog;
    private final AdminCronJobSchedulerManager schedulerManager;
    private final CurrentUserService currentUser;
    private final FinanceAlertProperties alertProps;
    private final RobinhoodRhDailyTrackerProperties rhDailyTrackerProps;
    private final RobinhoodRhCryptoTrackerProperties rhCryptoTrackerProps;
    private final RobinhoodRhCryptoAutoTradeProperties rhCryptoAutoTradeProps;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticAutoTradeProperties autoTradeProps;
    private final FinancePredictsProperties predictsProps;

    @Transactional
    public void bootstrapAndStart() {
        List<AdminCronJob> defaults = builtinCatalog.builtInDefaults(
                alertProps, rhDailyTrackerProps, rhCryptoTrackerProps, rhCryptoAutoTradeProps, agenticProps, autoTradeProps, predictsProps);
        for (AdminCronJob def : defaults) {
            if (!jobRepository.existsById(def.getJobKey())) {
                jobRepository.save(def);
                log.info("Seeded built-in cron job {}", def.getJobKey());
            }
        }
        refreshScheduler();
    }

    @Transactional(readOnly = true)
    public List<AdminCronJobDto> listJobs() {
        requireAppAdmin();
        return jobRepository.findAll().stream()
                .sorted(Comparator.comparing(AdminCronJob::getCategory)
                        .thenComparing(AdminCronJob::getDisplayName))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCronJobRunnerDto> listRunners() {
        requireAppAdmin();
        return runnerRegistry.allRunners().stream()
                .map(r -> new AdminCronJobRunnerDto(
                        r.runnerKey(), r.label(), r.description(), r.category()))
                .toList();
    }

    @Transactional
    public AdminCronJobDto createJob(AdminCronJobUpsertRequestDto request) {
        requireAppAdmin();
        validateUpsert(request, true);
        AdminCronJob job = new AdminCronJob();
        job.setJobKey("custom." + UUID.randomUUID().toString().substring(0, 8));
        job.setBuiltIn(false);
        applyUpsert(job, request);
        stampTimestampsIfNew(job);
        schedulerManager.rescheduleJob(job);
        job = jobRepository.save(job);
        log.info("Created custom cron job {} ({})", job.getJobKey(), job.getRunnerKey());
        return toDto(job);
    }

    @Transactional
    public AdminCronJobDto updateJob(String jobKey, AdminCronJobUpsertRequestDto request) {
        requireAppAdmin();
        AdminCronJob job = requireJob(jobKey);
        validateUpsert(request, false);
        applyUpsert(job, request);
        schedulerManager.rescheduleJob(job);
        job = jobRepository.save(job);
        log.info("Updated cron job {}", jobKey);
        return toDto(job);
    }

    @Transactional
    public void deleteJob(String jobKey) {
        requireAppAdmin();
        AdminCronJob job = requireJob(jobKey);
        if (job.isBuiltIn()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Built-in jobs cannot be deleted; disable instead.");
        }
        jobRepository.delete(job);
        List<AdminCronJob> remaining = jobRepository.findAll();
        schedulerManager.rescheduleAll(remaining);
        jobRepository.saveAll(remaining);
        log.info("Deleted custom cron job {}", jobKey);
    }

    public AdminCronJobActionResultDto runNow(String jobKey) {
        requireAppAdmin();
        AdminCronJob job = requireJob(jobKey);
        if (!runnerRegistry.find(job.getRunnerKey()).filter(AdminCronJobRunnerDefinition::isAvailable).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Runner is not available in this environment: " + job.getRunnerKey());
        }
        schedulerManager.enqueueRunNow(job);
        log.info("Enqueued manual run for cron job {}", jobKey);
        return new AdminCronJobActionResultDto(true, jobKey, "Job started", Instant.now());
    }

    private void refreshScheduler() {
        List<AdminCronJob> jobs = jobRepository.findAll();
        schedulerManager.rescheduleAll(jobs);
        jobRepository.saveAll(jobs);
    }

    private void requireAppAdmin() {
        if (!currentUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    private AdminCronJob requireJob(String jobKey) {
        return jobRepository
                .findById(jobKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown cron job: " + jobKey));
    }

    private void validateUpsert(AdminCronJobUpsertRequestDto request, boolean creating) {
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
        }
        if (request.runnerKey() == null || request.runnerKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "runnerKey is required");
        }
        if (runnerRegistry.find(request.runnerKey()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown runner: " + request.runnerKey());
        }
        String scheduleType = request.scheduleType();
        if (scheduleType == null || scheduleType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduleType is required");
        }
        if ("CRON".equals(scheduleType)) {
            if (request.cronExpression() == null || request.cronExpression().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cronExpression is required for CRON jobs");
            }
            try {
                AdminCronJobScheduleSupport.normalizeCronExpression(request.cronExpression());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron expression: " + e.getMessage());
            }
            if (request.zoneId() == null || request.zoneId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zoneId is required for CRON jobs");
            }
        } else if ("FIXED_DELAY".equals(scheduleType)) {
            if (request.fixedDelayMs() == null || request.fixedDelayMs() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fixedDelayMs must be > 0 for FIXED_DELAY jobs");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduleType must be CRON or FIXED_DELAY");
        }
        if (creating && request.category() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category is required");
        }
    }

    private void applyUpsert(AdminCronJob job, AdminCronJobUpsertRequestDto request) {
        job.setDisplayName(request.displayName().trim());
        job.setDescription(trimOrNull(request.description()));
        if (request.category() != null && !request.category().isBlank()) {
            job.setCategory(request.category().trim());
        } else if (job.getCategory() == null) {
            runnerRegistry
                    .find(request.runnerKey())
                    .ifPresent(r -> job.setCategory(r.category()));
        }
        job.setScheduleType(request.scheduleType());
        job.setRunnerKey(request.runnerKey().trim());
        if ("CRON".equals(request.scheduleType())) {
            job.setCronExpression(AdminCronJobScheduleSupport.normalizeCronExpression(request.cronExpression()));
            job.setFixedDelayMs(null);
            job.setZoneId(request.zoneId().trim());
        } else {
            job.setCronExpression(null);
            job.setFixedDelayMs(request.fixedDelayMs());
            job.setZoneId(request.zoneId() != null && !request.zoneId().isBlank() ? request.zoneId().trim() : "UTC");
        }
        job.setInitialDelayMs(request.initialDelayMs() == null ? 0L : Math.max(0L, request.initialDelayMs()));
        if (request.enabled() != null) {
            job.setEnabled(request.enabled());
        }
    }

    private AdminCronJobDto toDto(AdminCronJob job) {
        var runner = runnerRegistry.find(job.getRunnerKey());
        return new AdminCronJobDto(
                job.getJobKey(),
                job.getDisplayName(),
                job.getDescription(),
                job.getCategory(),
                job.getScheduleType(),
                job.getCronExpression(),
                job.getFixedDelayMs(),
                job.getInitialDelayMs(),
                job.getZoneId(),
                job.isEnabled(),
                job.isBuiltIn(),
                job.getRunnerKey(),
                runner.map(AdminCronJobRunnerDefinition::label).orElse(job.getRunnerKey()),
                job.getLastRunAt(),
                job.getLastRunStatus(),
                job.getLastRunMessage(),
                job.getNextRunAt(),
                AdminCronJobScheduleSupport.scheduleSummary(job),
                runner.map(AdminCronJobRunnerDefinition::isAvailable).orElse(false));
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void stampTimestampsIfNew(AdminCronJob job) {
        Instant now = Instant.now();
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(now);
        }
        job.setUpdatedAt(now);
    }
}
