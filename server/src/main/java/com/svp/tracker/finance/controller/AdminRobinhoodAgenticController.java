package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.RobinhoodAgenticOrderDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsRequestDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminActionResultDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminConfigDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminDefaultsDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminDefaultsRequestDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminStatsDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminTrackerDto;
import com.svp.tracker.finance.service.AdminRobinhoodAgenticService;
import com.svp.tracker.finance.service.RobinhoodAgenticAdminDefaultsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/finance/agentic")
@RequiredArgsConstructor
@Slf4j
public class AdminRobinhoodAgenticController {

    private final AdminRobinhoodAgenticService adminService;
    private final RobinhoodAgenticAdminDefaultsService defaultsService;

    @GetMapping("/config")
    public RobinhoodAgenticAdminConfigDto config() {
        return adminService.config();
    }

    @GetMapping("/stats")
    public RobinhoodAgenticAdminStatsDto stats() {
        return adminService.stats();
    }

    @GetMapping("/tracker")
    public RobinhoodAgenticAdminTrackerDto tracker() {
        return adminService.tracker();
    }

    @GetMapping("/defaults")
    public RobinhoodAgenticAdminDefaultsDto defaults() {
        return defaultsService.getDefaults();
    }

    @PutMapping("/defaults")
    public RobinhoodAgenticAdminDefaultsDto saveDefaults(@RequestBody RobinhoodAgenticAdminDefaultsRequestDto body) {
        log.info("Admin updated Robinhood Agentic default guardrails");
        return defaultsService.saveDefaults(body);
    }

    @PostMapping("/actions/evaluate-all")
    public RobinhoodAgenticAdminActionResultDto evaluateAll() {
        log.info("Admin manual: evaluate-all auto-trade");
        return adminService.evaluateAll();
    }

    @PostMapping("/actions/evaluate/{userId}")
    public RobinhoodAgenticAdminActionResultDto evaluateUser(@PathVariable long userId) {
        log.info("Admin manual: evaluate auto-trade for user {}", userId);
        return adminService.evaluateUser(userId);
    }

    @PostMapping("/users/{userId}/defaults/apply")
    public RobinhoodAgenticAdminActionResultDto applyDefaultsToUser(@PathVariable long userId) {
        return adminService.applyDefaultsToUser(userId);
    }

    @GetMapping("/users/{userId}/settings")
    public RobinhoodAgenticSettingsDto settingsForUser(@PathVariable long userId) {
        return adminService.settingsForUser(userId);
    }

    @PutMapping("/users/{userId}/settings")
    public RobinhoodAgenticSettingsDto saveSettingsForUser(
            @PathVariable long userId, @RequestBody RobinhoodAgenticSettingsRequestDto body) {
        return adminService.saveSettingsForUser(userId, body);
    }

    @PostMapping("/users/{userId}/orders/{orderId}/approve")
    public RobinhoodAgenticOrderDto approveOrder(@PathVariable long userId, @PathVariable long orderId) {
        log.info("Admin approved Agentic order {} for user {}", orderId, userId);
        return adminService.approveOrder(userId, orderId);
    }

    @PostMapping("/users/{userId}/orders/{orderId}/reject")
    public RobinhoodAgenticOrderDto rejectOrder(@PathVariable long userId, @PathVariable long orderId) {
        log.info("Admin rejected Agentic order {} for user {}", orderId, userId);
        return adminService.rejectOrder(userId, orderId);
    }
}
