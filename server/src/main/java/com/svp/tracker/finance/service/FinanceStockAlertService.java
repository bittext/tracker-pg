package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.finance.domain.FinanceStockAlert;
import com.svp.tracker.finance.domain.FinanceStockAlertRepeatMode;
import com.svp.tracker.finance.domain.FinanceStockAlertTriggerType;
import com.svp.tracker.finance.dto.FinanceAlertEventDto;
import com.svp.tracker.finance.dto.FinanceStockAlertDto;
import com.svp.tracker.finance.dto.FinanceStockAlertRequestDto;
import com.svp.tracker.finance.repository.FinanceAlertEventRepository;
import com.svp.tracker.finance.repository.FinanceStockAlertRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinanceStockAlertService {

    private final CurrentUserService currentUser;
    private final FinanceAlertProperties props;
    private final FinanceStockAlertRepository alertRepository;
    private final FinanceAlertEventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<FinanceStockAlertDto> listCurrentUserAlerts() {
        long ownerUserId = currentUser.requireUserId();
        return alertRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(FinanceAlertMapper::alert)
                .toList();
    }

    @Transactional
    public FinanceStockAlertDto createCurrentUserAlert(FinanceStockAlertRequestDto req) {
        long ownerUserId = currentUser.requireUserId();
        FinanceStockAlert alert = new FinanceStockAlert();
        alert.setOwnerUserId(ownerUserId);
        apply(alert, req);
        return FinanceAlertMapper.alert(alertRepository.save(alert));
    }

    @Transactional
    public FinanceStockAlertDto updateCurrentUserAlert(long id, FinanceStockAlertRequestDto req) {
        long ownerUserId = currentUser.requireUserId();
        FinanceStockAlert alert = alertRepository
                .findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        apply(alert, req);
        return FinanceAlertMapper.alert(alertRepository.save(alert));
    }

    @Transactional
    public void deleteCurrentUserAlert(long id) {
        long ownerUserId = currentUser.requireUserId();
        FinanceStockAlert alert = alertRepository
                .findByIdAndOwnerUserId(id, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        alertRepository.delete(alert);
    }

    @Transactional(readOnly = true)
    public List<FinanceAlertEventDto> listCurrentUserEvents(Integer limit) {
        long ownerUserId = currentUser.requireUserId();
        int capped = cap(limit);
        return eventRepository
                .findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId, PageRequest.of(0, capped))
                .stream()
                .map(FinanceAlertMapper::event)
                .toList();
    }

    private void apply(FinanceStockAlert alert, FinanceStockAlertRequestDto req) {
        String symbol = req.symbol() == null ? "" : req.symbol().trim().toUpperCase();
        if (!symbol.matches("^[A-Z0-9.\\-]{1,32}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbol is required and may contain letters, numbers, dot, or dash");
        }
        FinanceStockAlertTriggerType trigger = req.triggerType();
        if (trigger == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trigger type is required");
        }
        BigDecimal threshold = req.thresholdValue();
        if (threshold == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Threshold is required");
        }
        if (trigger == FinanceStockAlertTriggerType.PRICE_AT_OR_ABOVE && threshold.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target price must be greater than zero");
        }
        FinanceStockAlertRepeatMode repeat = req.repeatMode() == null ? FinanceStockAlertRepeatMode.ONCE : req.repeatMode();
        int cooldown = req.cooldownMinutes() == null ? props.defaultCooldownMinutes() : req.cooldownMinutes();
        if (cooldown < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cooldown minutes cannot be negative");
        }
        alert.setSymbol(symbol);
        alert.setTriggerType(trigger);
        alert.setThresholdValue(threshold);
        alert.setRepeatMode(repeat);
        alert.setCooldownMinutes(cooldown);
        alert.setEnabled(req.enabled() == null || req.enabled());
    }

    private int cap(Integer limit) {
        if (limit == null || limit < 1) {
            return props.maxEventsReturned();
        }
        return Math.min(limit, props.maxEventsReturned());
    }
}
