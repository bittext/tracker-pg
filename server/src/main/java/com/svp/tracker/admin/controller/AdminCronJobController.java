package com.svp.tracker.admin.controller;

import com.svp.tracker.admin.cron.AdminCronJobService;
import com.svp.tracker.admin.cron.dto.AdminCronJobActionResultDto;
import com.svp.tracker.admin.cron.dto.AdminCronJobDto;
import com.svp.tracker.admin.cron.dto.AdminCronJobRunnerDto;
import com.svp.tracker.admin.cron.dto.AdminCronJobUpsertRequestDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only surface for scheduled job management. Requires {@code ROLE_ADMIN} via
 * {@code SecurityConfig} ({@code /api/admin/**}) and re-checks in {@link AdminCronJobService}.
 */
@RestController
@RequestMapping("/api/admin/cron-jobs")
@RequiredArgsConstructor
@Slf4j
public class AdminCronJobController {

    private final AdminCronJobService cronJobService;

    @GetMapping
    public List<AdminCronJobDto> listJobs() {
        return cronJobService.listJobs();
    }

    @GetMapping("/runners")
    public List<AdminCronJobRunnerDto> listRunners() {
        return cronJobService.listRunners();
    }

    @PostMapping
    public AdminCronJobDto createJob(@RequestBody AdminCronJobUpsertRequestDto request) {
        log.info("Admin create cron job runnerKey={}", request.runnerKey());
        return cronJobService.createJob(request);
    }

    @PutMapping("/{jobKey}")
    public AdminCronJobDto updateJob(@PathVariable String jobKey, @RequestBody AdminCronJobUpsertRequestDto request) {
        log.info("Admin update cron job {}", jobKey);
        return cronJobService.updateJob(jobKey, request);
    }

    @DeleteMapping("/{jobKey}")
    public void deleteJob(@PathVariable String jobKey) {
        log.info("Admin delete cron job {}", jobKey);
        cronJobService.deleteJob(jobKey);
    }

    @PostMapping("/{jobKey}/run")
    public AdminCronJobActionResultDto runNow(@PathVariable String jobKey) {
        log.info("Admin manual run cron job {}", jobKey);
        return cronJobService.runNow(jobKey);
    }
}
