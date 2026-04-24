package com.svp.tracker.admin.controller;

import com.svp.tracker.finance.dto.FinanceNotificationSettingsDto;
import com.svp.tracker.finance.dto.FinanceNotificationSettingsRequestDto;
import com.svp.tracker.finance.dto.FinanceNotificationTestRequestDto;
import com.svp.tracker.finance.dto.FinanceNotificationTestResultDto;
import com.svp.tracker.finance.service.FinanceNotificationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/finance/notifications")
@RequiredArgsConstructor
@Slf4j
public class AdminFinanceNotificationsController {

    private final FinanceNotificationSettingsService settingsService;

    @GetMapping
    public FinanceNotificationSettingsDto getSettings() {
        return settingsService.getCurrentUserSettings();
    }

    @PutMapping
    public FinanceNotificationSettingsDto saveSettings(@RequestBody FinanceNotificationSettingsRequestDto req) {
        log.info("PUT /api/admin/finance/notifications emailEnabled={} smsEnabled={}", req.emailEnabled(), req.smsEnabled());
        return settingsService.saveCurrentUserSettings(req);
    }

    @PostMapping("/test")
    public FinanceNotificationTestResultDto testSettings(@RequestBody FinanceNotificationTestRequestDto req) {
        log.info("POST /api/admin/finance/notifications/test email={} sms={}", req.email(), req.sms());
        return settingsService.testCurrentUserSettings(req);
    }
}
