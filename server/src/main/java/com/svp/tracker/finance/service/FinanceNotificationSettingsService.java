package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.finance.domain.FinanceAlertEvent;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.dto.FinanceAlertEventDto;
import com.svp.tracker.finance.dto.FinanceNotificationSettingsDto;
import com.svp.tracker.finance.dto.FinanceNotificationSettingsRequestDto;
import com.svp.tracker.finance.dto.FinanceNotificationTestRequestDto;
import com.svp.tracker.finance.dto.FinanceNotificationTestResultDto;
import com.svp.tracker.finance.repository.FinanceNotificationSettingsRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinanceNotificationSettingsService {

    private final CurrentUserService currentUser;
    private final FinanceAlertProperties props;
    private final FinanceNotificationSettingsRepository settingsRepository;
    private final FinanceAlertDispatchService dispatchService;

    @Transactional(readOnly = true)
    public FinanceNotificationSettingsDto getCurrentUserSettings() {
        long ownerUserId = currentUser.requireUserId();
        return FinanceAlertMapper.settings(findOrEmpty(ownerUserId), props);
    }

    @Transactional
    public FinanceNotificationSettingsDto saveCurrentUserSettings(FinanceNotificationSettingsRequestDto req) {
        long ownerUserId = currentUser.requireUserId();
        FinanceNotificationSettings s = settingsRepository
                .findByOwnerUserId(ownerUserId)
                .orElseGet(() -> {
                    FinanceNotificationSettings n = new FinanceNotificationSettings();
                    n.setOwnerUserId(ownerUserId);
                    return n;
                });
        s.setEmailAddress(clean(req.emailAddress()));
        s.setMobileE164(clean(req.mobileE164()));
        s.setEmailEnabled(Boolean.TRUE.equals(req.emailEnabled()));
        s.setSmsEnabled(Boolean.TRUE.equals(req.smsEnabled()));
        validateSettings(s);
        return FinanceAlertMapper.settings(settingsRepository.save(s), props);
    }

    @Transactional
    public FinanceNotificationTestResultDto testCurrentUserSettings(FinanceNotificationTestRequestDto req) {
        long ownerUserId = currentUser.requireUserId();
        FinanceNotificationSettings s = settingsRepository
                .findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save notification settings first"));
        List<FinanceAlertEvent> events = new ArrayList<>();
        if (Boolean.TRUE.equals(req.email())) {
            events.add(dispatchService.testEmail(ownerUserId, s.getEmailAddress()));
        }
        if (Boolean.TRUE.equals(req.sms())) {
            events.add(dispatchService.testSms(ownerUserId, s.getMobileE164()));
        }
        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one channel to test");
        }
        return new FinanceNotificationTestResultDto(events.stream().map(FinanceAlertMapper::event).toList());
    }

    @Transactional(readOnly = true)
    public FinanceNotificationSettings findOrEmpty(long ownerUserId) {
        return settingsRepository.findByOwnerUserId(ownerUserId).orElseGet(() -> {
            FinanceNotificationSettings s = new FinanceNotificationSettings();
            s.setOwnerUserId(ownerUserId);
            return s;
        });
    }

    private static void validateSettings(FinanceNotificationSettings s) {
        if (s.isEmailEnabled() && (s.getEmailAddress() == null || !s.getEmailAddress().contains("@"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required when email is enabled");
        }
        if (s.isSmsEnabled()
                && (s.getMobileE164() == null || !s.getMobileE164().matches("^\\+[1-9][0-9]{7,14}$"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number must be E.164, e.g. +15551234567");
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
