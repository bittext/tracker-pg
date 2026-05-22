package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.ApplicationBranding;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.FinanceStockAlert;
import com.svp.tracker.finance.domain.FinanceStockAlertRepeatMode;
import com.svp.tracker.finance.domain.FinanceStockAlertTriggerType;
import com.svp.tracker.finance.dto.FinanceAlertEvaluationDto;
import com.svp.tracker.finance.dto.YahooExtendedQuoteDto;
import com.svp.tracker.finance.repository.FinanceStockAlertRepository;
import java.math.BigDecimal;
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
        Map<String, YahooExtendedQuoteDto> quotes =
                yahooBatchQuoteService.fetchExtendedForAlerts(List.copyOf(symbols));
        List<com.svp.tracker.finance.domain.FinanceAlertEvent> events = new ArrayList<>();
        int triggered = 0;
        for (FinanceStockAlert alert : alerts) {
            YahooExtendedQuoteDto q = quotes.get(alert.getSymbol());
            BigDecimal price = bd(q == null ? null : q.regularMarketPrice());
            BigDecimal change = bd(q == null ? null : q.regularMarketChangePercent());
            alert.setLastCheckedAt(evaluatedAt);
            alert.setLastRegularMarketPrice(price);
            alert.setLastRegularMarketChangePercent(change);
            syncCompanyName(alert, q);
            if (q == null || (price == null && change == null)) {
                continue;
            }
            updateTriggerArmed(alert, price, change);
            if (shouldFire(alert, price, change)) {
                triggered++;
                alert.setLastTriggeredAt(evaluatedAt);
                alert.setFireCount(alert.getFireCount() + 1);
                alert.setTriggerArmed(false);
                if (alert.getRepeatMode() == FinanceStockAlertRepeatMode.ONCE) {
                    alert.setEnabled(false);
                }
                FinanceNotificationSettings settings = settingsService.findOrEmpty(alert.getOwnerUserId());
                events.addAll(dispatchService.dispatchTriggeredAlert(
                        alert,
                        settings,
                        subject(alert, q),
                        body(alert, q, evaluatedAt, price, change)));
            }
        }
        alertRepository.saveAll(alerts);
        return new FinanceAlertEvaluationDto(
                evaluatedAt, alerts.size(), triggered, events.stream().map(FinanceAlertMapper::event).toList());
    }

    /** Re-arm repeating alerts after price/session drops below the threshold. */
    static void updateTriggerArmed(FinanceStockAlert alert, BigDecimal price, BigDecimal change) {
        if (isTriggered(alert, price, change)) {
            return;
        }
        alert.setTriggerArmed(true);
    }

    static boolean shouldFire(FinanceStockAlert alert, BigDecimal price, BigDecimal change) {
        if (!alert.isTriggerArmed()) {
            return false;
        }
        if (alert.getRepeatMode() == FinanceStockAlertRepeatMode.ONCE && alert.getLastTriggeredAt() != null) {
            return false;
        }
        return isTriggered(alert, price, change);
    }

    static boolean isTriggered(FinanceStockAlert alert, BigDecimal price, BigDecimal change) {
        if (alert.getTriggerType() == FinanceStockAlertTriggerType.PRICE_AT_OR_ABOVE) {
            return price != null && price.compareTo(alert.getThresholdValue()) >= 0;
        }
        return change != null && change.compareTo(alert.getThresholdValue()) >= 0;
    }

    private static void syncCompanyName(FinanceStockAlert alert, YahooExtendedQuoteDto q) {
        if (q == null) {
            return;
        }
        String name = pickCompanyName(q);
        if (name != null && !name.isBlank() && !name.equalsIgnoreCase(alert.getSymbol())) {
            alert.setCompanyName(name);
        }
    }

    private static String pickCompanyName(YahooExtendedQuoteDto q) {
        if (q.longName() != null && !q.longName().isBlank() && !q.longName().equalsIgnoreCase(q.symbol())) {
            return q.longName();
        }
        if (q.shortName() != null && !q.shortName().isBlank() && !q.shortName().equalsIgnoreCase(q.symbol())) {
            return q.shortName();
        }
        return null;
    }

    private static String resolveCompanyName(FinanceStockAlert alert, YahooExtendedQuoteDto q) {
        if (alert.getCompanyName() != null && !alert.getCompanyName().isBlank()) {
            return alert.getCompanyName();
        }
        String fromQuote = q == null ? null : pickCompanyName(q);
        if (fromQuote != null && !fromQuote.isBlank()) {
            return fromQuote;
        }
        return alert.getSymbol();
    }

    private static String subject(FinanceStockAlert alert, YahooExtendedQuoteDto q) {
        return ApplicationBranding.SHORT_NAME
                + " alert: "
                + alert.getSymbol()
                + " ("
                + resolveCompanyName(alert, q)
                + ") reached "
                + alert.getThresholdValue();
    }

    private static String body(
            FinanceStockAlert alert,
            YahooExtendedQuoteDto q,
            Instant now,
            BigDecimal price,
            BigDecimal change) {
        String observed = alert.getTriggerType() == FinanceStockAlertTriggerType.PRICE_AT_OR_ABOVE
                ? "price $" + fmt(price)
                : "session change " + fmt(change) + "%";
        return ApplicationBranding.SHORT_NAME + " finance alert fired at "
                + now
                + "\nSymbol: "
                + alert.getSymbol()
                + "\nName: "
                + resolveCompanyName(alert, q)
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

    private static String fmt(BigDecimal value) {
        return value == null ? "n/a" : String.format(java.util.Locale.US, "%,.2f", value);
    }
}
