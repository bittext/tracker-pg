package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.ApplicationBranding;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.finance.domain.FinanceAlertDeliveryStatus;
import com.svp.tracker.finance.domain.FinanceAlertEvent;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.FinanceStockAlert;
import com.svp.tracker.finance.domain.FinanceStockAlertRepeatMode;
import com.svp.tracker.finance.domain.FinanceStockAlertTriggerType;
import com.svp.tracker.finance.dto.FinanceAlertEvaluationDto;
import com.svp.tracker.finance.dto.YahooExtendedQuoteDto;
import com.svp.tracker.finance.repository.FinanceStockAlertRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceAlertEvaluationService {

    private final FinanceAlertProperties props;
    private final CurrentUserService currentUser;
    private final FinanceStockAlertRepository alertRepository;
    private final YahooBatchQuoteService yahooBatchQuoteService;
    private final FinanceNotificationSettingsService settingsService;
    private final FinanceAlertDispatchService dispatchService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${tracker.finance.alerts.poll-fixed-delay-ms:300000}")
    public void scheduledEvaluate() {
        if (!props.evaluationEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("Finance alert evaluation already running; skipping this tick");
            return;
        }
        try {
            evaluateAlerts(alertRepository.findByEnabledTrueOrderBySymbolAscIdAsc());
        } catch (Exception e) {
            log.warn("Finance alert evaluation failed", e);
        } finally {
            running.set(false);
        }
    }

    @Transactional
    public FinanceAlertEvaluationDto evaluateCurrentUserAlerts() {
        long ownerUserId = currentUser.requireUserId();
        List<FinanceStockAlert> alerts = alertRepository.findByOwnerUserIdAndEnabledTrueOrderBySymbolAscIdAsc(ownerUserId);
        return evaluateAlerts(alerts);
    }

    @Transactional
    public FinanceAlertEvaluationDto evaluateAlerts(List<FinanceStockAlert> alerts) {
        Instant evaluatedAt = Instant.now();
        if (alerts == null || alerts.isEmpty()) {
            return new FinanceAlertEvaluationDto(evaluatedAt, 0, 0, List.of());
        }
        Set<String> symbols = new LinkedHashSet<>();
        for (FinanceStockAlert a : alerts) {
            if (a.getSymbol() != null && !a.getSymbol().isBlank()) {
                symbols.add(a.getSymbol());
            }
        }
        Map<String, YahooExtendedQuoteDto> quotes = yahooBatchQuoteService.fetchExtendedBySymbols(List.copyOf(symbols));
        List<FinanceAlertEvent> events = new ArrayList<>();
        int triggered = 0;
        for (FinanceStockAlert alert : alerts) {
            YahooExtendedQuoteDto q = quotes.get(alert.getSymbol());
            BigDecimal price = bd(q == null ? null : q.regularMarketPrice());
            BigDecimal change = bd(q == null ? null : q.regularMarketChangePercent());
            alert.setLastCheckedAt(evaluatedAt);
            alert.setLastRegularMarketPrice(price);
            alert.setLastRegularMarketChangePercent(change);
            if (q == null || (price == null && change == null)) {
                continue;
            }
            if (isTriggered(alert, price, change) && canFire(alert, evaluatedAt)) {
                triggered++;
                alert.setLastTriggeredAt(evaluatedAt);
                alert.setFireCount(alert.getFireCount() + 1);
                if (alert.getRepeatMode() == FinanceStockAlertRepeatMode.ONCE) {
                    alert.setEnabled(false);
                }
                FinanceNotificationSettings settings = settingsService.findOrEmpty(alert.getOwnerUserId());
                events.addAll(dispatchService.dispatchTriggeredAlert(
                        alert,
                        settings,
                        subject(alert, q),
                        body(alert, q, evaluatedAt)));
            }
        }
        alertRepository.saveAll(alerts);
        return new FinanceAlertEvaluationDto(
                evaluatedAt, alerts.size(), triggered, events.stream().map(FinanceAlertMapper::event).toList());
    }

    private static boolean isTriggered(FinanceStockAlert alert, BigDecimal price, BigDecimal change) {
        if (alert.getTriggerType() == FinanceStockAlertTriggerType.PRICE_AT_OR_ABOVE) {
            return price != null && price.compareTo(alert.getThresholdValue()) >= 0;
        }
        return change != null && change.compareTo(alert.getThresholdValue()) >= 0;
    }

    private static boolean canFire(FinanceStockAlert alert, Instant now) {
        if (alert.getLastTriggeredAt() == null) {
            return true;
        }
        if (alert.getRepeatMode() == FinanceStockAlertRepeatMode.ONCE) {
            return false;
        }
        return Duration.between(alert.getLastTriggeredAt(), now).toMinutes() >= alert.getCooldownMinutes();
    }

    private static String subject(FinanceStockAlert alert, YahooExtendedQuoteDto q) {
        return ApplicationBranding.SHORT_NAME + " alert: " + alert.getSymbol() + " reached " + alert.getThresholdValue();
    }

    private static String body(FinanceStockAlert alert, YahooExtendedQuoteDto q, Instant now) {
        String observed = alert.getTriggerType() == FinanceStockAlertTriggerType.PRICE_AT_OR_ABOVE
                ? "price $" + fmt(q.regularMarketPrice())
                : "session change " + fmt(q.regularMarketChangePercent()) + "%";
        return ApplicationBranding.SHORT_NAME + " finance alert fired at "
                + now
                + "\nSymbol: "
                + alert.getSymbol()
                + "\nName: "
                + (q.longName() == null || q.longName().isBlank() ? q.shortName() : q.longName())
                + "\nCondition: "
                + alert.getTriggerType()
                + " >= "
                + alert.getThresholdValue()
                + "\nObserved: "
                + observed
                + "\nThis is an automated market-data alert; quotes may be delayed.";
    }

    private static BigDecimal bd(Double d) {
        return d == null ? null : BigDecimal.valueOf(d);
    }

    private static String fmt(Double d) {
        return d == null ? "n/a" : String.format(java.util.Locale.US, "%,.2f", d);
    }
}
