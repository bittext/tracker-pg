package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceAlertProperties;
import com.svp.tracker.config.WebProperties;
import com.svp.tracker.finance.domain.FinanceNotificationSettings;
import com.svp.tracker.finance.domain.RhIndividualMarginAlertEvent;
import com.svp.tracker.finance.domain.RhIndividualMarginPeek;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.dto.RhMarginSnapshotMoneyRow;
import com.svp.tracker.finance.dto.RobinhoodIndividualMarginAlertEventDto;
import com.svp.tracker.finance.dto.RobinhoodIndividualMarginDayDto;
import com.svp.tracker.finance.dto.RobinhoodIndividualMarginPeekDto;
import com.svp.tracker.finance.dto.RobinhoodIndividualMarginPeekResultDto;
import com.svp.tracker.finance.dto.RobinhoodIndividualMarginStandingDto;
import com.svp.tracker.finance.dto.RobinhoodIndividualMarginWatchDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountsTrackDto;
import com.svp.tracker.finance.dto.RobinhoodRhMarginDetailsDto;
import com.svp.tracker.finance.repository.RhIndividualMarginAlertEventRepository;
import com.svp.tracker.finance.repository.RhIndividualMarginPeekRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import com.svp.tracker.mail.OutboundEmailSender;
import com.svp.tracker.member.repository.MemberProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodIndividualMarginWatchService {

    public static final String ACCOUNT_SUFFIX = RobinhoodIndividualMarginMath.ACCOUNT_SUFFIX;
    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final Duration STALE_AFTER = Duration.ofMinutes(90);
    private static final DateTimeFormatter CAPTURE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z").withZone(CENTRAL);

    private final CurrentUserService currentUser;
    private final RobinhoodRhAccountsTrackService rhAccountsTrackService;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RhIndividualMarginPeekRepository peekRepository;
    private final RhIndividualMarginAlertEventRepository alertRepository;
    private final FinanceNotificationSettingsService notificationSettingsService;
    private final FinanceAlertProperties alertProps;
    private final WebProperties webProperties;
    private final MemberProfileRepository memberProfileRepository;
    private final OutboundEmailSender outboundEmailSender;

    @Transactional
    public RobinhoodIndividualMarginWatchDto watch(int year, boolean sync) {
        long ownerUserId = currentUser.requireUserId();
        backfillScheduled(ownerUserId, year);
        if (sync || latestIsStale(ownerUserId)) {
            try {
                recordLive(ownerUserId, Instant.now(), RobinhoodRhDailyCaptureKind.MANUAL, true, sync);
            } catch (RuntimeException e) {
                log.warn("Individual margin live peek skipped for user {}: {}", ownerUserId, e.getMessage());
            }
        }
        return buildWatch(ownerUserId, year);
    }

    @Transactional
    public RobinhoodIndividualMarginPeekResultDto peekNow() {
        long ownerUserId = currentUser.requireUserId();
        Instant at = Instant.now();
        Optional<RhIndividualMarginPeek> saved =
                recordLive(ownerUserId, at, RobinhoodRhDailyCaptureKind.MANUAL, true, true);
        int year = at.atZone(CENTRAL).getYear();
        if (saved.isEmpty()) {
            return new RobinhoodIndividualMarginPeekResultDto(
                    false,
                    "Individual ••••3370 was not in the live Robinhood pull.",
                    buildWatch(ownerUserId, year));
        }
        return new RobinhoodIndividualMarginPeekResultDto(
                true, "Saved a live Individual ••••3370 margin peek.", buildWatch(ownerUserId, year));
    }

    @Transactional
    public void recordFromCapture(
            long ownerUserId, Instant capturedAt, String captureKind, RobinhoodRhAccountsTrackDto track) {
        if (track == null || track.accounts() == null) {
            return;
        }
        findIndividual(track).ifPresent(acct -> persistFromAccount(ownerUserId, capturedAt, captureKind, acct, true));
    }

    private Optional<RhIndividualMarginPeek> recordLive(
            long ownerUserId, Instant capturedAt, String captureKind, boolean evaluateAlerts, boolean syncLatest) {
        RobinhoodRhAccountsTrackDto track = rhAccountsTrackService.buildForOwner(ownerUserId, syncLatest);
        return findIndividual(track)
                .map(acct -> persistFromAccount(ownerUserId, capturedAt, captureKind, acct, evaluateAlerts));
    }

    private boolean latestIsStale(long ownerUserId) {
        return peekRepository
                .findTopByOwnerUserIdAndAccountSuffixOrderByCapturedAtDesc(ownerUserId, ACCOUNT_SUFFIX)
                .map(p -> p.getCapturedAt() == null || Duration.between(p.getCapturedAt(), Instant.now()).compareTo(STALE_AFTER) > 0)
                .orElse(true);
    }

    private void backfillScheduled(long ownerUserId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<RhMarginSnapshotMoneyRow> rows =
                snapshotRepository.findScheduledMoneyForSuffixBetween(ownerUserId, ACCOUNT_SUFFIX, from, to);
        for (RhMarginSnapshotMoneyRow row : rows) {
            if (row.snapshotAt() == null) {
                continue;
            }
            if (peekRepository
                    .findByOwnerUserIdAndAccountSuffixAndCapturedAt(ownerUserId, ACCOUNT_SUFFIX, row.snapshotAt())
                    .isPresent()) {
                continue;
            }
            persistComputed(
                    ownerUserId,
                    row.snapshotAt(),
                    row.snapshotDate(),
                    row.captureKind() == null ? RobinhoodRhDailyCaptureKind.SCHEDULED : row.captureKind(),
                    row.cashBalance(),
                    row.equityMarketValue(),
                    row.totalAccountValue(),
                    null,
                    null,
                    null,
                    false);
        }
    }

    private RhIndividualMarginPeek persistFromAccount(
            long ownerUserId,
            Instant capturedAt,
            String captureKind,
            RobinhoodRhAccountSummaryDto acct,
            boolean evaluateAlerts) {
        RobinhoodRhMarginDetailsDto margin = acct.margin();
        return persistComputed(
                ownerUserId,
                capturedAt,
                capturedAt.atZone(CENTRAL).toLocalDate(),
                captureKind,
                acct.cashBalance(),
                acct.equityMarketValue(),
                acct.totalAccountValue(),
                margin == null ? null : margin.optionsValue(),
                margin == null ? null : margin.buyingPower(),
                margin == null ? null : margin.unleveragedBuyingPower(),
                evaluateAlerts);
    }

    private RhIndividualMarginPeek persistComputed(
            long ownerUserId,
            Instant capturedAt,
            LocalDate snapshotDate,
            String captureKind,
            BigDecimal cash,
            BigDecimal equity,
            BigDecimal portfolio,
            BigDecimal optionsValue,
            BigDecimal buyingPower,
            BigDecimal unleveragedBuyingPower,
            boolean evaluateAlerts) {
        Optional<RhIndividualMarginPeek> priorOpt =
                peekRepository.findTopByOwnerUserIdAndAccountSuffixOrderByCapturedAtDesc(ownerUserId, ACCOUNT_SUFFIX);
        RhIndividualMarginPeek peek = peekRepository
                .findByOwnerUserIdAndAccountSuffixAndCapturedAt(ownerUserId, ACCOUNT_SUFFIX, capturedAt)
                .orElseGet(RhIndividualMarginPeek::new);
        boolean created = peek.getId() == null;

        applyComputed(
                peek,
                ownerUserId,
                capturedAt,
                snapshotDate,
                captureKind,
                cash,
                equity,
                portfolio,
                optionsValue,
                buyingPower,
                unleveragedBuyingPower);
        if (peek.getCreatedAt() == null) {
            peek.setCreatedAt(Instant.now());
        }
        RhIndividualMarginPeek saved = peekRepository.save(peek);

        if (evaluateAlerts && created) {
            RhIndividualMarginPeek prior = priorOpt
                    .filter(p -> p.getCapturedAt() != null && p.getCapturedAt().isBefore(capturedAt))
                    .orElse(null);
            evaluateCallBand(ownerUserId, saved, prior);
        }
        return saved;
    }

    static void applyComputed(
            RhIndividualMarginPeek peek,
            long ownerUserId,
            Instant capturedAt,
            LocalDate snapshotDate,
            String captureKind,
            BigDecimal cash,
            BigDecimal equity,
            BigDecimal portfolio,
            BigDecimal optionsValue,
            BigDecimal buyingPower,
            BigDecimal unleveragedBuyingPower) {
        BigDecimal debit = RobinhoodIndividualMarginMath.marginDebit(cash);
        BigDecimal equityMv = RobinhoodIndividualMarginMath.scaleMoney(equity);
        BigDecimal book = RobinhoodIndividualMarginMath.scaleMoney(portfolio);
        BigDecimal borrow = RobinhoodIndividualMarginMath.borrowPercent(debit, equityMv);
        BigDecimal maint = RobinhoodIndividualMarginMath.estimatedMaintenance(equityMv);
        BigDecimal buffer = RobinhoodIndividualMarginMath.bufferAmount(book, maint);
        BigDecimal bufferPct = RobinhoodIndividualMarginMath.bufferPercent(buffer, book);
        String risk = RobinhoodIndividualMarginMath.riskStatus(bufferPct);

        peek.setOwnerUserId(ownerUserId);
        peek.setAccountSuffix(ACCOUNT_SUFFIX);
        peek.setCapturedAt(capturedAt);
        peek.setSnapshotDate(snapshotDate);
        peek.setCaptureKind(captureKind);
        peek.setCashBalance(RobinhoodIndividualMarginMath.scaleMoney(cash));
        peek.setEquityMarketValue(equityMv);
        peek.setPortfolioValue(book);
        peek.setOptionsValue(optionsValue == null ? null : RobinhoodIndividualMarginMath.scaleMoney(optionsValue));
        peek.setBuyingPower(buyingPower == null ? null : RobinhoodIndividualMarginMath.scaleMoney(buyingPower));
        peek.setUnleveragedBuyingPower(
                unleveragedBuyingPower == null
                        ? null
                        : RobinhoodIndividualMarginMath.scaleMoney(unleveragedBuyingPower));
        peek.setMarginDebit(debit);
        peek.setBorrowPercent(borrow);
        peek.setAnnualRatePercent(RobinhoodIndividualMarginMath.ANNUAL_RATE_PERCENT);
        peek.setDailyInterest(RobinhoodIndividualMarginMath.dailyInterest(debit));
        peek.setMaintenanceRequirement(maint);
        peek.setMaintenanceSource(RobinhoodIndividualMarginMath.MAINTENANCE_SOURCE_ESTIMATED);
        peek.setBufferAmount(buffer);
        peek.setBufferPercent(bufferPct);
        peek.setNearCall(RobinhoodIndividualMarginMath.nearCall(bufferPct));
        peek.setHighBorrow(RobinhoodIndividualMarginMath.highBorrow(borrow));
        peek.setRiskStatus(risk);
    }

    private void evaluateCallBand(long ownerUserId, RhIndividualMarginPeek current, RhIndividualMarginPeek prior) {
        boolean wasNear = prior != null && prior.isNearCall();
        boolean nowNear = current.isNearCall();
        if (prior == null && !nowNear) {
            return;
        }
        if (nowNear == wasNear) {
            return;
        }
        String kind = nowNear ? "NEAR_CALL" : "LEFT_NEAR_CALL";
        sendCallBandEmail(ownerUserId, current, kind);
    }

    private void sendCallBandEmail(long ownerUserId, RhIndividualMarginPeek current, String kind) {
        String subject = nowNearSubject(kind, current);
        String body = callBandBody(current, kind);
        FinanceNotificationSettings settings = notificationSettingsService.findOrEmpty(ownerUserId);
        String to = settings.getEmailAddress();
        RhIndividualMarginAlertEvent event = new RhIndividualMarginAlertEvent();
        event.setOwnerUserId(ownerUserId);
        event.setAccountSuffix(ACCOUNT_SUFFIX);
        event.setPeekId(current.getId());
        event.setEventKind(kind);
        event.setBufferPercent(current.getBufferPercent());
        event.setBorrowPercent(current.getBorrowPercent());
        event.setCreatedAt(Instant.now());

        if (!settings.isEmailEnabled() || to == null || !to.contains("@")) {
            event.setEmailStatus("SKIPPED");
            event.setDestinationMasked(maskEmail(to));
            event.setDetail("Finance email notifications are disabled or email address is missing");
            alertRepository.save(event);
            return;
        }
        if (!memberAllowsEmail(ownerUserId)) {
            event.setEmailStatus("SKIPPED");
            event.setDestinationMasked(maskEmail(to));
            event.setDetail("Email notifications are turned off in member profile");
            alertRepository.save(event);
            return;
        }
        if (!alertProps.emailProviderConfigured()) {
            event.setEmailStatus("FAILED");
            event.setDestinationMasked(maskEmail(to));
            event.setDetail("Outbound email is not configured");
            alertRepository.save(event);
            return;
        }
        OutboundEmailSender.SendOutcome outcome =
                outboundEmailSender.sendPlainText(alertProps.emailFrom(), List.of(to.trim()), subject, body, null);
        if (!outcome.success()) {
            event.setEmailStatus("FAILED");
            event.setDestinationMasked(maskEmail(to));
            event.setDetail(truncate(outcome.errorDetail(), 500));
            alertRepository.save(event);
            return;
        }
        event.setEmailStatus("SENT");
        event.setDestinationMasked(maskEmail(to));
        event.setDetail(kind + " alert sent");
        alertRepository.save(event);
    }

    private String nowNearSubject(String kind, RhIndividualMarginPeek current) {
        String buffer = current.getBufferPercent() == null
                ? "n/a"
                : current.getBufferPercent().setScale(2, RoundingMode.HALF_UP) + "%";
        if ("NEAR_CALL".equals(kind)) {
            return "Margin watch: Individual ••••3370 within 5% of a margin call (buffer " + buffer + ")";
        }
        return "Margin watch: Individual ••••3370 left the 5% call band (buffer " + buffer + ")";
    }

    private String callBandBody(RhIndividualMarginPeek current, String kind) {
        StringBuilder sb = new StringBuilder();
        sb.append("Individual brokerage ••••3370 margin watch\n\n");
        sb.append("Event: ").append("NEAR_CALL".equals(kind) ? "Entered the ±5% call band" : "Left the ±5% call band");
        sb.append('\n');
        sb.append("Captured: ").append(CAPTURE_TIME.format(current.getCapturedAt())).append('\n');
        sb.append("Status: ").append(RobinhoodIndividualMarginMath.riskLabel(current.getRiskStatus())).append('\n');
        sb.append("Margin used: ").append(formatMoney(current.getMarginDebit())).append('\n');
        sb.append("Margin available: ").append(formatMoney(current.getBuyingPower())).append('\n');
        sb.append("Equity book: ").append(formatMoney(current.getEquityMarketValue())).append('\n');
        sb.append("Borrow of book: ").append(formatPct(current.getBorrowPercent())).append('\n');
        sb.append("Portfolio: ").append(formatMoney(current.getPortfolioValue())).append('\n');
        sb.append("Estimated maintenance: ").append(formatMoney(current.getMaintenanceRequirement())).append('\n');
        sb.append("Buffer: ")
                .append(formatMoney(current.getBufferAmount()))
                .append(" (")
                .append(formatPct(current.getBufferPercent()))
                .append(")\n");
        sb.append("Daily interest (4.75% / 365): ").append(formatMoney(current.getDailyInterest())).append('\n');
        sb.append('\n');
        sb.append("Maintenance is estimated from Robinhood’s house print scaled to the current equity book. ");
        sb.append("A call is treated as buffer ÷ portfolio ≤ 5%.\n\n");
        sb.append(appLink());
        return sb.toString();
    }

    private RobinhoodIndividualMarginWatchDto buildWatch(long ownerUserId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<RhIndividualMarginPeek> yearPeeks = peekRepository
                .findByOwnerUserIdAndAccountSuffixAndSnapshotDateBetweenOrderByCapturedAtAsc(
                        ownerUserId, ACCOUNT_SUFFIX, from, to);
        List<RhIndividualMarginPeek> recent = new ArrayList<>(peekRepository
                .findTop48ByOwnerUserIdAndAccountSuffixOrderByCapturedAtDesc(ownerUserId, ACCOUNT_SUFFIX));
        recent.sort(Comparator.comparing(RhIndividualMarginPeek::getCapturedAt));

        List<RobinhoodIndividualMarginPeekDto> yearDtos = withDeltas(yearPeeks);
        Map<LocalDate, RobinhoodIndividualMarginPeekDto> lastByDay = new LinkedHashMap<>();
        for (RobinhoodIndividualMarginPeekDto dto : yearDtos) {
            lastByDay.put(dto.snapshotDate(), dto);
        }
        List<RobinhoodIndividualMarginDayDto> days = new ArrayList<>();
        RobinhoodIndividualMarginPeekDto prevDay = null;
        for (RobinhoodIndividualMarginPeekDto close : lastByDay.values()) {
            BigDecimal debitChange = prevDay == null ? null : close.marginDebit().subtract(prevDay.marginDebit());
            BigDecimal borrowChange = prevDay == null ? null : close.borrowPercent().subtract(prevDay.borrowPercent());
            days.add(new RobinhoodIndividualMarginDayDto(close.snapshotDate(), close, debitChange, borrowChange));
            prevDay = close;
        }

        RhIndividualMarginPeek latestEntity = peekRepository
                .findTopByOwnerUserIdAndAccountSuffixOrderByCapturedAtDesc(ownerUserId, ACCOUNT_SUFFIX)
                .orElse(null);
        RobinhoodIndividualMarginPeekDto latest = latestEntity == null
                ? null
                : toDto(latestEntity, priorBefore(yearPeeks, latestEntity).orElse(null));

        FinanceNotificationSettings settings = notificationSettingsService.findOrEmpty(ownerUserId);
        boolean emailConfigured = settings.isEmailEnabled()
                && settings.getEmailAddress() != null
                && settings.getEmailAddress().contains("@");
        String emailHint = emailConfigured
                ? "Call-band alerts send to " + maskEmail(settings.getEmailAddress()) + "."
                : "Enable email in Admin → Finance → Notifications so a ±5% call-band alert can send.";

        List<String> notes = new ArrayList<>();
        notes.add("Individual brokerage ••••3370 only — Agentic, Ammu, IRA, and managed books are excluded.");
        notes.add(
                "Borrow % is margin debit ÷ equity book. Deep red starts at 50% of that book.");
        notes.add(
                "Near-call alerts fire when estimated buffer ÷ portfolio crosses 5% (enter or leave).");
        notes.add(
                "House maintenance is scaled from Robinhood’s Sep 27 2026 print; the broker does not expose the official requirement on this API.");
        notes.add("Hourly Daily Tracker captures keep the tape current; Peek now stores an extra print.");

        List<RobinhoodIndividualMarginAlertEventDto> alerts = alertRepository
                .findTop20ByOwnerUserIdAndAccountSuffixOrderByCreatedAtDesc(ownerUserId, ACCOUNT_SUFFIX)
                .stream()
                .map(this::toAlertDto)
                .toList();

        return new RobinhoodIndividualMarginWatchDto(
                ACCOUNT_SUFFIX,
                RobinhoodIndividualMarginMath.ACCOUNT_LABEL,
                year,
                latest,
                standing(latest),
                List.copyOf(days),
                withDeltas(recent),
                alerts,
                emailConfigured,
                emailHint,
                notes);
    }

    private static Optional<RhIndividualMarginPeek> priorBefore(
            List<RhIndividualMarginPeek> ordered, RhIndividualMarginPeek current) {
        RhIndividualMarginPeek prior = null;
        for (RhIndividualMarginPeek row : ordered) {
            if (row.getCapturedAt() != null
                    && current.getCapturedAt() != null
                    && !row.getCapturedAt().isBefore(current.getCapturedAt())) {
                break;
            }
            prior = row;
        }
        return Optional.ofNullable(prior);
    }

    private static List<RobinhoodIndividualMarginPeekDto> withDeltas(List<RhIndividualMarginPeek> ordered) {
        List<RobinhoodIndividualMarginPeekDto> out = new ArrayList<>();
        RhIndividualMarginPeek prior = null;
        for (RhIndividualMarginPeek row : ordered) {
            out.add(toDto(row, prior));
            prior = row;
        }
        return out;
    }

    private static RobinhoodIndividualMarginPeekDto toDto(RhIndividualMarginPeek row, RhIndividualMarginPeek prior) {
        BigDecimal debitDelta = prior == null ? null : row.getMarginDebit().subtract(prior.getMarginDebit());
        BigDecimal borrowDelta = prior == null ? null : row.getBorrowPercent().subtract(prior.getBorrowPercent());
        return new RobinhoodIndividualMarginPeekDto(
                row.getId(),
                row.getCapturedAt(),
                row.getSnapshotDate(),
                row.getCaptureKind(),
                row.getCashBalance(),
                row.getEquityMarketValue(),
                row.getPortfolioValue(),
                row.getOptionsValue(),
                row.getBuyingPower(),
                row.getUnleveragedBuyingPower(),
                row.getMarginDebit(),
                row.getBorrowPercent(),
                row.getAnnualRatePercent(),
                row.getDailyInterest(),
                row.getMaintenanceRequirement(),
                row.getMaintenanceSource(),
                row.getBufferAmount(),
                row.getBufferPercent(),
                row.isNearCall(),
                row.isHighBorrow(),
                row.getRiskStatus(),
                RobinhoodIndividualMarginMath.riskLabel(row.getRiskStatus()),
                debitDelta,
                borrowDelta);
    }

    private static RobinhoodIndividualMarginStandingDto standing(RobinhoodIndividualMarginPeekDto latest) {
        if (latest == null) {
            return new RobinhoodIndividualMarginStandingDto(
                    "No Individual ••••3370 margin peeks yet.",
                    "UNKNOWN",
                    "Unknown",
                    "unknown",
                    null,
                    null,
                    null,
                    null,
                    "Wait for a Daily Tracker capture or click Peek now.");
        }
        BigDecimal maintShare = latest.portfolioValue() == null
                        || latest.portfolioValue().signum() == 0
                        || latest.maintenanceRequirement() == null
                ? null
                : latest.maintenanceRequirement()
                        .multiply(new BigDecimal("100"))
                        .divide(latest.portfolioValue(), 2, RoundingMode.HALF_UP);
        BigDecimal monthInterest = latest.dailyInterest() == null
                ? null
                : latest.dailyInterest().multiply(new BigDecimal("30")).setScale(2, RoundingMode.HALF_UP);
        String tone = toneFor(latest);
        String headline;
        if (latest.nearCall()) {
            headline = "Inside the broker’s 5% call band — buffer is "
                    + formatPct(latest.bufferPercent())
                    + " of portfolio.";
        } else if (latest.highBorrow()) {
            headline = "Debit is "
                    + formatPct(latest.borrowPercent())
                    + " of the equity book — deep-red borrow zone.";
        } else {
            headline = "Debit is "
                    + formatPct(latest.borrowPercent())
                    + " of the equity book · buffer "
                    + formatPct(latest.bufferPercent())
                    + ".";
        }
        return new RobinhoodIndividualMarginStandingDto(
                headline,
                latest.riskStatus(),
                latest.riskLabel(),
                tone,
                latest.borrowPercent(),
                latest.bufferPercent(),
                maintShare,
                monthInterest,
                "Estimated house maintenance scaled from Robinhood’s Sep 27 2026 print.");
    }

    static String toneFor(RobinhoodIndividualMarginPeekDto latest) {
        if (latest == null) {
            return "unknown";
        }
        if ("CALL".equals(latest.riskStatus()) || "NEAR_CALL".equals(latest.riskStatus())) {
            return "call";
        }
        if (latest.highBorrow()) {
            return "deep";
        }
        if ("HIGH".equals(latest.riskStatus()) || "ELEVATED".equals(latest.riskStatus())) {
            return "hot";
        }
        return "ok";
    }

    private RobinhoodIndividualMarginAlertEventDto toAlertDto(RhIndividualMarginAlertEvent e) {
        return new RobinhoodIndividualMarginAlertEventDto(
                e.getId(),
                e.getEventKind(),
                e.getBufferPercent(),
                e.getBorrowPercent(),
                e.getEmailStatus(),
                e.getDestinationMasked(),
                e.getDetail(),
                e.getCreatedAt());
    }

    private static Optional<RobinhoodRhAccountSummaryDto> findIndividual(RobinhoodRhAccountsTrackDto track) {
        if (track == null || track.accounts() == null) {
            return Optional.empty();
        }
        return track.accounts().stream()
                .filter(a -> ACCOUNT_SUFFIX.equals(a.accountSuffix()))
                .findFirst();
    }

    private boolean memberAllowsEmail(long ownerUserId) {
        return memberProfileRepository
                .findByUserId(ownerUserId)
                .map(p -> p.isMarketingEmailOptIn())
                .orElse(true);
    }

    private String appLink() {
        String url = webProperties.publicAppUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return "Open Insights → Margin: " + url + "/life/insights";
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) {
            return "$0.00";
        }
        return String.format(Locale.US, "$%,.2f", v);
    }

    private static String formatPct(BigDecimal v) {
        if (v == null) {
            return "n/a";
        }
        return v.setScale(2, RoundingMode.HALF_UP) + "%";
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
}
