package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.config.WebProperties;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.RhDailyTrackerAccountAlert;
import com.svp.tracker.finance.domain.RhDailyTrackerAlertEvent;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RhDailyTrackerAccountAlertDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAccountAlertItemDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAccountAlertSaveRequestDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAccountAlertsDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAlertEventDto;
import com.svp.tracker.finance.dto.RhDailyTrackerAlertTestResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountsTrackDto;
import com.svp.tracker.finance.repository.RhDailyTrackerAccountAlertRepository;
import com.svp.tracker.finance.repository.RhDailyTrackerAlertEventRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import com.svp.tracker.mail.OutboundEmailSender;
import com.svp.tracker.member.repository.MemberProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhDailyTrackerAlertService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter CAPTURE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z").withZone(CENTRAL);

    private final RobinhoodRhDailyTrackerProperties dailyTrackerProps;
    private final FinanceAlertProperties alertProps;
    private final WebProperties webProperties;
    private final CurrentUserService currentUser;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final RobinhoodRhAccountsTrackService rhAccountsTrackService;
    private final FinanceNotificationSettingsService notificationSettingsService;
    private final MemberProfileRepository memberProfileRepository;
    private final RhDailyTrackerAccountAlertRepository alertRepository;
    private final RhDailyTrackerAlertEventRepository eventRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final OutboundEmailSender outboundEmailSender;

    @Transactional(readOnly = true)
    public RhDailyTrackerAccountAlertsDto listCurrentUserAlerts() {
        long ownerUserId = currentUser.requireUserId();
        return buildAlertsDto(ownerUserId);
    }

    @Transactional
    public RhDailyTrackerAccountAlertsDto saveCurrentUserAlerts(RhDailyTrackerAccountAlertSaveRequestDto req) {
        long ownerUserId = currentUser.requireUserId();
        if (req == null || req.accounts() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accounts is required");
        }
        Set<String> allowed = trackedAccountSuffixes(ownerUserId);
        Instant now = Instant.now();
        for (RhDailyTrackerAccountAlertItemDto item : req.accounts()) {
            if (item == null || item.accountSuffix() == null || item.accountSuffix().isBlank()) {
                continue;
            }
            String suffix = item.accountSuffix().trim();
            if (!allowed.contains(suffix)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Account ••••" + suffix + " is not in your Daily Tracker");
            }
            validateAlertItem(item);
            RhDailyTrackerAccountAlert row = alertRepository
                    .findByOwnerUserIdAndAccountSuffix(ownerUserId, suffix)
                    .orElseGet(() -> {
                        RhDailyTrackerAccountAlert n = new RhDailyTrackerAccountAlert();
                        n.setOwnerUserId(ownerUserId);
                        n.setAccountSuffix(suffix);
                        n.setCreatedAt(now);
                        return n;
                    });
            applyItem(row, item, now);
            alertRepository.save(row);
        }
        return buildAlertsDto(ownerUserId);
    }

    @Transactional(readOnly = true)
    public List<RhDailyTrackerAlertEventDto> recentEvents(int limit) {
        long ownerUserId = currentUser.requireUserId();
        int cap = Math.min(Math.max(limit, 1), 50);
        return eventRepository.findTop20ByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .limit(cap)
                .map(this::toEventDto)
                .toList();
    }

    @Transactional
    public RhDailyTrackerAlertTestResultDto sendTestEmail() {
        long ownerUserId = currentUser.requireUserId();
        RhDailyTrackerAccountAlertsDto settings = buildAlertsDto(ownerUserId);
        RhDailyTrackerAccountAlertDto firstEnabled = settings.accounts().stream()
                .filter(RhDailyTrackerAccountAlertDto::enabled)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Enable at least one account alert before testing"));
        String subject = "Daily Tracker alert test: ••••" + firstEnabled.accountSuffix();
        String body = "This is a test Daily Tracker spike alert.\n\n"
                + "Account: "
                + firstEnabled.label()
                + " (••••"
                + firstEnabled.accountSuffix()
                + ")\n"
                + "Configure thresholds on Reports → Robinhood Daily Tracker → Spike alerts.\n\n"
                + appLink();
        RhDailyTrackerAlertEvent event = sendAlertEmail(ownerUserId, firstEnabled.accountSuffix(), subject, body);
        return new RhDailyTrackerAlertTestResultDto(
                "SENT".equals(event.getEmailStatus()) ? "Test email sent" : "Test email not sent: " + event.getDetail(),
                toEventDto(event));
    }

    /** Called after snapshots are saved during capture. */
    @Transactional
    public void evaluateAfterCapture(long ownerUserId, List<RobinhoodRhDailySnapshot> captured) {
        if (!dailyTrackerProps.alertsEnabled() || captured == null || captured.isEmpty()) {
            return;
        }
        Map<String, RhDailyTrackerAccountAlert> configsBySuffix = alertRepository
                .findByOwnerUserIdOrderByAccountSuffixAsc(ownerUserId)
                .stream()
                .filter(RhDailyTrackerAccountAlert::isEnabled)
                .collect(Collectors.toMap(RhDailyTrackerAccountAlert::getAccountSuffix, c -> c, (a, b) -> a));
        if (configsBySuffix.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (RobinhoodRhDailySnapshot current : captured) {
            if (current == null || current.getAccountSuffix() == null) {
                continue;
            }
            RhDailyTrackerAccountAlert config = configsBySuffix.get(current.getAccountSuffix().trim());
            if (config == null) {
                continue;
            }
            evaluateOne(ownerUserId, config, current, now);
        }
    }

    static boolean shouldFire(
            RhDailyTrackerAccountAlert config,
            BigDecimal absDeltaDollars,
            Optional<BigDecimal> absDeltaPercent,
            boolean positionsChanged) {
        if (config == null || !config.isEnabled()) {
            return false;
        }
        boolean anyTrigger = false;
        boolean fired = false;
        if (config.isValueDollarsEnabled()) {
            anyTrigger = true;
            BigDecimal min = config.getMinValueChangeDollars();
            if (min != null && absDeltaDollars.compareTo(min) >= 0) {
                fired = true;
            }
        }
        if (config.isValuePercentEnabled()) {
            anyTrigger = true;
            BigDecimal min = config.getMinValueChangePercent();
            if (min != null && absDeltaPercent.isPresent() && absDeltaPercent.get().compareTo(min) >= 0) {
                fired = true;
            }
        }
        if (config.isPositionChangeEnabled()) {
            anyTrigger = true;
            if (positionsChanged) {
                fired = true;
            }
        }
        return anyTrigger && fired;
    }

    static List<String> firedReasons(
            RhDailyTrackerAccountAlert config,
            BigDecimal absDeltaDollars,
            Optional<BigDecimal> absDeltaPercent,
            boolean positionsChanged) {
        List<String> reasons = new ArrayList<>();
        if (config.isValueDollarsEnabled()
                && config.getMinValueChangeDollars() != null
                && absDeltaDollars.compareTo(config.getMinValueChangeDollars()) >= 0) {
            reasons.add("VALUE_DOLLARS");
        }
        if (config.isValuePercentEnabled()
                && config.getMinValueChangePercent() != null
                && absDeltaPercent.isPresent()
                && absDeltaPercent.get().compareTo(config.getMinValueChangePercent()) >= 0) {
            reasons.add("VALUE_PERCENT");
        }
        if (config.isPositionChangeEnabled() && positionsChanged) {
            reasons.add("POSITIONS");
        }
        return reasons;
    }

    static boolean withinCooldown(RhDailyTrackerAccountAlert config, Instant now) {
        if (config.getLastTriggeredAt() == null || config.getCooldownMinutes() <= 0) {
            return false;
        }
        return Duration.between(config.getLastTriggeredAt(), now).toMinutes() < config.getCooldownMinutes();
    }

    private void evaluateOne(
            long ownerUserId, RhDailyTrackerAccountAlert config, RobinhoodRhDailySnapshot current, Instant now) {
        if (current.getId() != null
                && current.getId().equals(config.getLastTriggeredSnapshotId())) {
            return;
        }
        if (withinCooldown(config, now)) {
            return;
        }
        Optional<RobinhoodRhDailySnapshot> priorOpt = RobinhoodRhDailySnapshotCompare.findPriorSnapshot(
                snapshotRepository, ownerUserId, current.getAccountSuffix(), current.getSnapshotAt());
        if (priorOpt.isEmpty()) {
            return;
        }
        RobinhoodRhDailySnapshot prior = priorOpt.get();
        BigDecimal deltaDollars = RobinhoodRhDailySnapshotCompare.deltaDollars(prior, current);
        BigDecimal absDeltaDollars = deltaDollars.abs();
        Optional<BigDecimal> absDeltaPercent = RobinhoodRhDailySnapshotCompare.deltaPercentAbs(prior, current);
        boolean positionsChanged = RobinhoodRhDailySnapshotCompare.positionsChanged(prior, current);

        if (!shouldFire(config, absDeltaDollars, absDeltaPercent, positionsChanged)) {
            return;
        }

        List<String> reasons = firedReasons(config, absDeltaDollars, absDeltaPercent, positionsChanged);
        String subject = buildSubject(current.getAccountSuffix(), deltaDollars, absDeltaPercent.orElse(null));
        String body = buildBody(current, prior, deltaDollars, absDeltaPercent.orElse(null), positionsChanged, reasons);

        RhDailyTrackerAlertEvent event = sendAlertEmail(ownerUserId, current.getAccountSuffix(), subject, body);
        event.setSnapshotId(current.getId());
        event.setPriorSnapshotId(prior.getId());
        event.setTriggerReasons(String.join(",", reasons));
        event.setDeltaDollars(deltaDollars);
        event.setDeltaPercent(absDeltaPercent.orElse(null));
        eventRepository.save(event);

        if ("SENT".equals(event.getEmailStatus())) {
            config.setLastTriggeredAt(now);
            config.setLastTriggeredSnapshotId(current.getId());
            config.setUpdatedAt(now);
            alertRepository.save(config);
        }
    }

    private RhDailyTrackerAlertEvent sendAlertEmail(
            long ownerUserId, String accountSuffix, String subject, String body) {
        FinanceNotificationSettings settings = notificationSettingsService.findOrEmpty(ownerUserId);
        String to = settings.getEmailAddress();
        if (!settings.isEmailEnabled() || to == null || !to.contains("@")) {
            return audit(
                    ownerUserId,
                    accountSuffix,
                    "SKIPPED",
                    maskEmail(to),
                    "Finance email notifications are disabled or email address is missing — configure Admin → Finance → Notifications");
        }
        if (!memberAllowsEmail(ownerUserId)) {
            return audit(
                    ownerUserId,
                    accountSuffix,
                    "SKIPPED",
                    maskEmail(to),
                    "Email notifications are turned off in member profile (My profile)");
        }
        if (!alertProps.emailProviderConfigured()) {
            return audit(ownerUserId, accountSuffix, "FAILED", maskEmail(to), "Outbound email is not configured");
        }
        OutboundEmailSender.SendOutcome outcome =
                outboundEmailSender.sendPlainText(alertProps.emailFrom(), List.of(to.trim()), subject, body, null);
        if (!outcome.success()) {
            return audit(ownerUserId, accountSuffix, "FAILED", maskEmail(to), outcome.errorDetail());
        }
        return audit(ownerUserId, accountSuffix, "SENT", maskEmail(to), "Daily Tracker spike alert sent");
    }

    private RhDailyTrackerAccountAlertsDto buildAlertsDto(long ownerUserId) {
        FinanceNotificationSettings settings = notificationSettingsService.findOrEmpty(ownerUserId);
        boolean emailConfigured = settings.isEmailEnabled()
                && settings.getEmailAddress() != null
                && settings.getEmailAddress().contains("@");
        String emailHint = emailConfigured
                ? "Alerts send to "
                        + maskEmail(settings.getEmailAddress())
                        + " (Admin → Finance → Notifications)."
                : "Enable email in Admin → Finance → Notifications before spike alerts can send.";

        Map<String, RhDailyTrackerAccountAlert> saved = alertRepository.findByOwnerUserIdOrderByAccountSuffixAsc(ownerUserId)
                .stream()
                .collect(Collectors.toMap(RhDailyTrackerAccountAlert::getAccountSuffix, a -> a, (a, b) -> a, LinkedHashMap::new));

        List<RhDailyTrackerAccountAlertDto> accounts = new ArrayList<>();
        for (AccountMeta meta : trackedAccounts(ownerUserId)) {
            RhDailyTrackerAccountAlert row = saved.get(meta.suffix());
            if (row != null) {
                accounts.add(toAlertDto(meta, row));
            } else {
                accounts.add(defaultAlertDto(meta));
            }
        }
        return new RhDailyTrackerAccountAlertsDto(emailConfigured, emailHint, accounts);
    }

    private List<AccountMeta> trackedAccounts(long ownerUserId) {
        RobinhoodRhAccountsTrackDto track = rhAccountsTrackService.buildForOwner(ownerUserId, false);
        List<AccountMeta> out = new ArrayList<>();
        for (RobinhoodRhAccountSummaryDto acct : track.accounts()) {
            String suffix = acct.accountSuffix();
            if (suffix == null || suffix.isBlank()) {
                continue;
            }
            if (!accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, suffix)) {
                continue;
            }
            out.add(new AccountMeta(
                    suffix.trim(),
                    RobinhoodRhDailyTrackerAccountPolicy.displayLabel(suffix),
                    acct.accountKind()));
        }
        out.sort(Comparator.comparing(AccountMeta::suffix));
        return out;
    }

    private Set<String> trackedAccountSuffixes(long ownerUserId) {
        return trackedAccounts(ownerUserId).stream().map(AccountMeta::suffix).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static RhDailyTrackerAccountAlertDto defaultAlertDto(AccountMeta meta) {
        return new RhDailyTrackerAccountAlertDto(
                meta.suffix(),
                meta.label(),
                meta.accountKind(),
                false,
                false,
                null,
                false,
                null,
                false,
                60);
    }

    private static RhDailyTrackerAccountAlertDto toAlertDto(AccountMeta meta, RhDailyTrackerAccountAlert row) {
        return new RhDailyTrackerAccountAlertDto(
                meta.suffix(),
                meta.label(),
                meta.accountKind(),
                row.isEnabled(),
                row.isValueDollarsEnabled(),
                row.getMinValueChangeDollars(),
                row.isValuePercentEnabled(),
                row.getMinValueChangePercent(),
                row.isPositionChangeEnabled(),
                row.getCooldownMinutes());
    }

    private static void applyItem(RhDailyTrackerAccountAlert row, RhDailyTrackerAccountAlertItemDto item, Instant now) {
        row.setEnabled(item.enabled());
        row.setValueDollarsEnabled(item.valueDollarsEnabled());
        row.setMinValueChangeDollars(item.minValueChangeDollars());
        row.setValuePercentEnabled(item.valuePercentEnabled());
        row.setMinValueChangePercent(item.minValueChangePercent());
        row.setPositionChangeEnabled(item.positionChangeEnabled());
        row.setCooldownMinutes(item.cooldownMinutes() == null ? 60 : Math.max(0, item.cooldownMinutes()));
        row.setUpdatedAt(now);
    }

    private static void validateAlertItem(RhDailyTrackerAccountAlertItemDto item) {
        if (!item.enabled()) {
            return;
        }
        boolean anyTrigger = item.valueDollarsEnabled() || item.valuePercentEnabled() || item.positionChangeEnabled();
        if (!anyTrigger) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enable at least one trigger ($, %, or position change) for ••••" + item.accountSuffix());
        }
        if (item.valueDollarsEnabled()) {
            if (item.minValueChangeDollars() == null || item.minValueChangeDollars().signum() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Min $ change must be positive for ••••" + item.accountSuffix());
            }
        }
        if (item.valuePercentEnabled()) {
            if (item.minValueChangePercent() == null || item.minValueChangePercent().signum() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Min % change must be positive for ••••" + item.accountSuffix());
            }
        }
    }

    private String buildSubject(String suffix, BigDecimal deltaDollars, BigDecimal absDeltaPercent) {
        String direction = deltaDollars.signum() >= 0 ? "up" : "down";
        StringBuilder sb = new StringBuilder("Daily Tracker alert: ••••");
        sb.append(suffix).append(' ').append(direction).append(' ');
        sb.append(formatMoney(deltaDollars.abs()));
        if (absDeltaPercent != null) {
            sb.append(" (").append(absDeltaPercent.setScale(2, RoundingMode.HALF_UP)).append("%)");
        }
        return sb.toString();
    }

    private String buildBody(
            RobinhoodRhDailySnapshot current,
            RobinhoodRhDailySnapshot prior,
            BigDecimal deltaDollars,
            BigDecimal absDeltaPercent,
            boolean positionsChanged,
            List<String> reasons) {
        StringBuilder sb = new StringBuilder();
        sb.append("Robinhood Daily Tracker spike detected\n\n");
        sb.append("Account: ").append(current.getLabel()).append(" (••••").append(current.getAccountSuffix()).append(")\n");
        sb.append("Triggers: ").append(String.join(", ", reasons)).append('\n');
        sb.append("Prior capture: ")
                .append(CAPTURE_TIME.format(prior.getSnapshotAt()))
                .append(" (")
                .append(captureKindLabel(prior.getCaptureKind()))
                .append(") — ")
                .append(formatMoney(prior.getTotalAccountValue()))
                .append('\n');
        sb.append("Current capture: ")
                .append(CAPTURE_TIME.format(current.getSnapshotAt()))
                .append(" (")
                .append(captureKindLabel(current.getCaptureKind()))
                .append(") — ")
                .append(formatMoney(current.getTotalAccountValue()))
                .append('\n');
        sb.append("Change: ").append(formatSignedMoney(deltaDollars));
        if (absDeltaPercent != null) {
            sb.append(" (").append(absDeltaPercent.setScale(2, RoundingMode.HALF_UP)).append("% vs prior total)");
        }
        sb.append('\n');
        if (positionsChanged) {
            sb.append("Holdings quantities changed since the prior pull.\n");
        }
        sb.append('\n').append(appLink()).append('\n');
        return sb.toString();
    }

    private String appLink() {
        String url = webProperties.publicAppUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return "Open Daily Tracker: " + url + "/reports/finance/robinhood/daily-tracker";
    }

    private static String captureKindLabel(String kind) {
        if (RobinhoodRhDailyCaptureKind.MANUAL.equals(kind)) {
            return "manual";
        }
        if (RobinhoodRhDailyCaptureKind.INTRADAY.equals(kind)) {
            return "hourly";
        }
        return "scheduled";
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) {
            return "$0.00";
        }
        return String.format(Locale.US, "$%,.2f", v);
    }

    private static String formatSignedMoney(BigDecimal v) {
        if (v == null) {
            return "$0.00";
        }
        String sign = v.signum() >= 0 ? "+" : "-";
        return sign + String.format(Locale.US, "$%,.2f", v.abs());
    }

    private RhDailyTrackerAlertEvent audit(
            long ownerUserId, String suffix, String status, String destMasked, String detail) {
        RhDailyTrackerAlertEvent e = new RhDailyTrackerAlertEvent();
        e.setOwnerUserId(ownerUserId);
        e.setAccountSuffix(suffix);
        e.setEmailStatus(status);
        e.setDestinationMasked(destMasked);
        e.setDetail(truncate(detail, 500));
        e.setTriggerReasons("TEST");
        return eventRepository.save(e);
    }

    private RhDailyTrackerAlertEventDto toEventDto(RhDailyTrackerAlertEvent e) {
        return new RhDailyTrackerAlertEventDto(
                e.getId(),
                e.getAccountSuffix(),
                e.getSnapshotId(),
                e.getPriorSnapshotId(),
                e.getTriggerReasons(),
                e.getDeltaDollars(),
                e.getDeltaPercent(),
                e.getEmailStatus(),
                e.getDestinationMasked(),
                e.getDetail(),
                e.getCreatedAt());
    }

    private boolean memberAllowsEmail(long ownerUserId) {
        return memberProfileRepository
                .findByUserId(ownerUserId)
                .map(p -> p.isMarketingEmailOptIn())
                .orElse(true);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record AccountMeta(String suffix, String label, String accountKind) {}
}
