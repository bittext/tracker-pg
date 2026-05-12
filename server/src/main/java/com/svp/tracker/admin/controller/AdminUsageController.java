package com.svp.tracker.admin.controller;

import com.svp.tracker.admin.dto.usage.DailyActivityPointDto;
import com.svp.tracker.admin.dto.usage.FeatureUsageDto;
import com.svp.tracker.admin.dto.usage.MemberUsageDto;
import com.svp.tracker.admin.dto.usage.SignInDailyPointDto;
import com.svp.tracker.admin.dto.usage.UsageSummaryDto;
import com.svp.tracker.admin.service.AdminUsageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only application usage / adoption metrics for the Admin → Usage tab.
 * ADMIN role is enforced upstream by {@code SecurityConfig} via {@code /api/admin/**}.
 */
@RestController
@RequestMapping("/api/admin/usage")
@RequiredArgsConstructor
public class AdminUsageController {

    private final AdminUsageService service;

    @GetMapping("/summary")
    public UsageSummaryDto summary() {
        return service.summary();
    }

    @GetMapping("/feature-usage")
    public List<FeatureUsageDto> featureUsage(@RequestParam(required = false, defaultValue = "30") int days) {
        return service.featureUsage(days);
    }

    @GetMapping("/activity-timeseries")
    public List<DailyActivityPointDto> activityTimeseries(
            @RequestParam(required = false, defaultValue = "30") int days) {
        return service.dailyActivity(days);
    }

    @GetMapping("/sign-ins-timeseries")
    public List<SignInDailyPointDto> signInsTimeseries(
            @RequestParam(required = false, defaultValue = "30") int days) {
        return service.signInDaily(days);
    }

    @GetMapping("/members")
    public List<MemberUsageDto> members(@RequestParam(required = false, defaultValue = "30") int days) {
        return service.members(days);
    }
}
