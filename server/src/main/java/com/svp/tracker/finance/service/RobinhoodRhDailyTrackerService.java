package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.domain.RobinhoodRhDailyDayNote;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RobinhoodRhAccountSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountsTrackDto;
import com.svp.tracker.finance.dto.RobinhoodRhCashFlowEventDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyCaptureResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyDayNoteResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyManualCaptureDeleteResultDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailySnapshotDetailDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerAccountCellDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerAccountColumnDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerDayDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerManualCaptureAccountDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerManualCaptureDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerReportDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailyDayNoteRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Daily 9 PM Central Robinhood account snapshots for Reports → Daily Tracker. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhDailyTrackerService {

    /** Only this user's Daily Tracker runs the nightly scheduled capture and 9 PM UI labels. */
    public static final String SCHEDULED_CAPTURE_OWNER_USERNAME =
            RobinhoodAccountTrackerConfigService.FULL_DAILY_TRACKER_OWNER_USERNAME;

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter MANUAL_TIME =
            DateTimeFormatter.ofPattern("h:mm a").withZone(CENTRAL);

    private final CurrentUserService currentUser;
    private final AppUserRepository appUserRepository;
    private final RobinhoodRhAccountsTrackService rhAccountsTrackService;
    private final RobinhoodAgenticService agenticService;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodRhDailyDayNoteRepository dayNoteRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhDailyTrackerProperties dailyTrackerProps;
    private final ObjectProvider<RobinhoodRhDailyTrackerService> selfProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Whether nightly scheduled snapshots and 9 PM schedule copy apply to this owner. */
    public boolean isScheduledCaptureOwner(long ownerUserId) {
        return appUserRepository
                .findById(ownerUserId)
                .map(u -> SCHEDULED_CAPTURE_OWNER_USERNAME.equalsIgnoreCase(u.getUsername().trim()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public RobinhoodRhDailyTrackerReportDto buildReport(int year, Integer month) {
        long ownerUserId = currentUser.requireUserId();
        boolean scheduledOwner = isScheduledCaptureOwner(ownerUserId);
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<RobinhoodRhDailySnapshot> allYearRows =
                visibleSnapshots(
                        ownerUserId,
                        snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                                ownerUserId, yearStart, yearEnd));

        List<RobinhoodRhDailySnapshot> scheduledYearRows = scheduledOnly(allYearRows);
        List<RobinhoodRhDailySnapshot> manualYearRows = manualOnly(allYearRows);

        LocalDate filterFrom = month != null ? LocalDate.of(year, month, 1) : yearStart;
        LocalDate filterTo = month != null ? YearMonth.of(year, month).atEndOfMonth() : yearEnd;

        List<RobinhoodRhDailySnapshot> scheduledRows = scheduledYearRows.stream()
                .filter(r -> !r.getSnapshotDate().isBefore(filterFrom) && !r.getSnapshotDate().isAfter(filterTo))
                .toList();
        List<RobinhoodRhDailySnapshot> manualRows = manualYearRows.stream()
                .filter(r -> !r.getSnapshotDate().isBefore(filterFrom) && !r.getSnapshotDate().isAfter(filterTo))
                .toList();

        LinkedHashSet<String> suffixOrder = new LinkedHashSet<>();
        Map<String, RobinhoodRhDailyTrackerAccountColumnDto> columnBySuffix = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot row : allYearRows) {
            if (row.getSnapshotDate().isBefore(filterFrom) || row.getSnapshotDate().isAfter(filterTo)) {
                continue;
            }
            suffixOrder.add(row.getAccountSuffix());
            columnBySuffix.putIfAbsent(
                    row.getAccountSuffix(),
                    new RobinhoodRhDailyTrackerAccountColumnDto(
                            row.getAccountSuffix(), row.getLabel(), row.getAccountKind()));
        }

        Map<LocalDate, List<RobinhoodRhDailySnapshot>> scheduledByDate = new TreeMap<>(Comparator.reverseOrder());
        for (RobinhoodRhDailySnapshot row : scheduledRows) {
            scheduledByDate.computeIfAbsent(row.getSnapshotDate(), k -> new ArrayList<>()).add(row);
        }

        Map<LocalDate, List<RobinhoodRhDailySnapshot>> manualByDate = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot row : manualRows) {
            manualByDate.computeIfAbsent(row.getSnapshotDate(), k -> new ArrayList<>()).add(row);
        }

        Set<LocalDate> dayDates = new TreeSet<>(Comparator.reverseOrder());
        dayDates.addAll(scheduledByDate.keySet());
        dayDates.addAll(manualByDate.keySet());

        Map<LocalDate, String> summaryNotesByDate = new LinkedHashMap<>();
        for (RobinhoodRhDailyDayNote noteRow :
                dayNoteRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
                        ownerUserId, filterFrom, filterTo)) {
            if (noteRow.getNoteText() != null && !noteRow.getNoteText().isBlank()) {
                summaryNotesByDate.put(noteRow.getSnapshotDate(), noteRow.getNoteText().trim());
            }
        }

        List<RobinhoodRhDailyTrackerDayDto> days = new ArrayList<>();
        for (LocalDate dayDate : dayDates) {
            List<RobinhoodRhDailySnapshot> dayScheduled =
                    scheduledByDate.getOrDefault(dayDate, List.of());
            dayScheduled = new ArrayList<>(dayScheduled);
            dayScheduled.sort(Comparator.comparing(RobinhoodRhDailySnapshot::getAccountSuffix));

            Instant snapshotAt = dayScheduled.stream()
                    .map(RobinhoodRhDailySnapshot::getSnapshotAt)
                    .max(Instant::compareTo)
                    .orElse(null);

            BigDecimal combinedTotal = BigDecimal.ZERO;
            BigDecimal combinedAdded = BigDecimal.ZERO;
            BigDecimal combinedRemoved = BigDecimal.ZERO;
            BigDecimal combinedValueChange = BigDecimal.ZERO;
            List<RobinhoodRhDailyTrackerAccountCellDto> cells = new ArrayList<>();

            for (RobinhoodRhDailySnapshot row : dayScheduled) {
                combinedTotal = combinedTotal.add(nullToZero(row.getTotalAccountValue()));
                combinedAdded = combinedAdded.add(nullToZero(row.getPeriodAdded()));
                combinedRemoved = combinedRemoved.add(nullToZero(row.getPeriodRemoved()));
                combinedValueChange = combinedValueChange.add(nullToZero(row.getPeriodValueChange()));
                boolean flowActivity =
                        row.getPeriodAdded().signum() != 0 || row.getPeriodRemoved().signum() != 0;
                cells.add(new RobinhoodRhDailyTrackerAccountCellDto(
                        row.getId(),
                        row.getAccountSuffix(),
                        scaleMoney(row.getTotalAccountValue()),
                        scaleMoney(row.getPeriodAdded()),
                        scaleMoney(row.getPeriodRemoved()),
                        scaleMoney(row.getPeriodValueChange()),
                        flowActivity));
            }

            List<RobinhoodRhDailyTrackerManualCaptureDto> manualCaptures =
                    buildManualCapturesForDay(manualByDate.getOrDefault(dayDate, List.of()));

            days.add(new RobinhoodRhDailyTrackerDayDto(
                    dayDate,
                    snapshotAt,
                    !dayScheduled.isEmpty(),
                    scaleMoney(combinedTotal),
                    scaleMoney(combinedAdded),
                    scaleMoney(combinedRemoved),
                    scaleMoney(combinedValueChange),
                    cells,
                    manualCaptures,
                    summaryNotesByDate.getOrDefault(dayDate, "")));
        }

        List<RobinhoodRhDailySnapshot> monthScheduledRows = month != null
                ? scheduledYearRows.stream()
                        .filter(r -> r.getSnapshotDate().getMonthValue() == month)
                        .toList()
                : scheduledYearRows;

        BigDecimal monthCombinedTotal = latestCombinedTotal(monthScheduledRows);
        BigDecimal monthCombinedChange = combinedChange(monthScheduledRows);
        BigDecimal yearCombinedTotal = latestCombinedTotal(scheduledYearRows);
        BigDecimal yearCombinedChange = combinedChange(scheduledYearRows);

        List<String> notes = new ArrayList<>();
        boolean dailyTrackerEnabled = accountTrackerConfigService.isDailyTrackerEnabled(ownerUserId);
        String username = appUserRepository
                .findById(ownerUserId)
                .map(u -> RobinhoodRhDailyTrackerAccountPolicy.normalizeUsername(u.getUsername()))
                .orElse("");

        if (!dailyTrackerEnabled) {
            notes.add(
                    "Daily Tracker is not enabled for your account. Only spulickal (pulickal-agentic) and nisha (nisha-agentic) are configured by default.");
            notes.add("Other users see no data until an administrator adds your username under tracker.finance.rh-daily-tracker.additional-owner-suffixes.");
        } else if (RobinhoodRhDailyTrackerAccountPolicy.SPULICKAL_USERNAME.equals(username)) {
            if (dailyTrackerProps.snapshotSchedulerActive()) {
                notes.add(
                        "Automatic capture runs "
                                + dailyTrackerProps.autoCaptureScheduleLabel()
                                + " for pulickal-agentic accounts (syncs holdings first).");
            } else {
                notes.add(
                        "Automatic daily capture is disabled — use Capture now or enable the scheduler in server config.");
            }
            notes.add(
                    "pulickal-agentic Daily Tracker: ••••3550 (Agentic, tradable), ••••3370 (default individual), ••••4123 (managed), ••••8696 (Ammu’s Acc — linked, not agentic-tradable).");
            notes.add("Excluded from Daily Tracker: ••••0440 (Short Term Idv), ••••2835 (Roth IRA).");
            notes.add(
                    "Each day shows the scheduled 9 PM snapshot. Add call-summary notes in the expanded day panel.");
            notes.add("Period flows on scheduled rows are cash movements since the previous 9 PM snapshot.");
            if (days.isEmpty()) {
                notes.add(
                        "No snapshots yet — wait for the 9 PM job or click Capture now after connecting pulickal-agentic.");
            }
        } else if (RobinhoodRhDailyTrackerAccountPolicy.NISHA_USERNAME.equals(username)) {
            notes.add("nisha-agentic only — Daily Tracker: ••••4190 (default), ••••7581 (Agentic). Pulickal-agentic accounts are never shown.");
            notes.add("Use Capture now after syncing holdings from your nisha-agentic profile.");
            notes.add("Each day shows captures for that calendar date. Add call-summary notes in the expanded day panel.");
            notes.add("Period flows are cash movements since the previous snapshot on that account.");
            if (days.isEmpty()) {
                notes.add("No snapshots yet — connect nisha-agentic, sync, then click Capture now.");
            }
        } else {
            notes.add("Daily Tracker shows only the account suffixes configured for your username in server config.");
            notes.add("Use Capture now after connecting your Robinhood Agentic sync and syncing holdings.");
            notes.add("Each day shows captures for that calendar date. Add call-summary notes in the expanded day panel.");
            notes.add("Period flows are cash movements since the previous snapshot on that account.");
            if (days.isEmpty()) {
                notes.add("No snapshots yet — connect Agentic Trading, sync, then click Capture now.");
            }
        }

        boolean autoCaptureScheduled = scheduledOwner && dailyTrackerProps.snapshotSchedulerActive();
        return new RobinhoodRhDailyTrackerReportDto(
                year,
                month,
                monthCombinedTotal,
                monthCombinedChange,
                yearCombinedTotal,
                yearCombinedChange,
                autoCaptureScheduled,
                autoCaptureScheduled ? dailyTrackerProps.autoCaptureScheduleLabel() : "",
                suffixOrder.stream().map(columnBySuffix::get).filter(Objects::nonNull).toList(),
                days,
                notes);
    }

    @Transactional(readOnly = true)
    public RobinhoodRhDailySnapshotDetailDto getSnapshotDetail(long snapshotId) {
        long ownerUserId = currentUser.requireUserId();
        RobinhoodRhDailySnapshot row = snapshotRepository
                .findByIdAndOwnerUserId(snapshotId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found"));
        if (isHiddenAccount(ownerUserId, row.getAccountSuffix())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found");
        }
        return toDetailDto(row);
    }

    @Transactional
    public RobinhoodRhDailyDayNoteResultDto upsertDaySummaryNote(LocalDate snapshotDate, String noteText) {
        long ownerUserId = currentUser.requireUserId();
        String trimmed = noteText == null ? "" : noteText.trim();
        Optional<RobinhoodRhDailyDayNote> existing =
                dayNoteRepository.findByOwnerUserIdAndSnapshotDate(ownerUserId, snapshotDate);
        if (trimmed.isEmpty()) {
            existing.ifPresent(dayNoteRepository::delete);
            return new RobinhoodRhDailyDayNoteResultDto(snapshotDate, "", "Summary note cleared.");
        }
        Instant now = Instant.now();
        RobinhoodRhDailyDayNote row = existing.orElseGet(RobinhoodRhDailyDayNote::new);
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        row.setOwnerUserId(ownerUserId);
        row.setSnapshotDate(snapshotDate);
        row.setNoteText(trimmed);
        row.setUpdatedAt(now);
        dayNoteRepository.save(row);
        return new RobinhoodRhDailyDayNoteResultDto(snapshotDate, trimmed, "Summary note saved.");
    }

    @Transactional
    public RobinhoodRhDailyManualCaptureDeleteResultDto deleteManualCapture(Instant capturedAt) {
        long ownerUserId = currentUser.requireUserId();
        List<RobinhoodRhDailySnapshot> rows = snapshotRepository.findByOwnerUserIdAndSnapshotAtAndCaptureKind(
                ownerUserId, capturedAt, RobinhoodRhDailyCaptureKind.MANUAL);
        List<RobinhoodRhDailySnapshot> visible = rows.stream()
                .filter(r -> !isHiddenAccount(ownerUserId, r.getAccountSuffix()))
                .toList();
        if (visible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Manual capture not found");
        }
        snapshotRepository.deleteAll(visible);
        String message = "Deleted manual capture at "
                + MANUAL_TIME.format(capturedAt)
                + " Central ("
                + visible.size()
                + " account"
                + (visible.size() == 1 ? "" : "s")
                + ").";
        log.info("RH manual capture delete for user {} at {}: {}", ownerUserId, capturedAt, message);
        return new RobinhoodRhDailyManualCaptureDeleteResultDto(true, visible.size(), message);
    }

    public RobinhoodRhDailyCaptureResultDto captureNow(boolean syncLatest) {
        long ownerUserId = currentUser.requireUserId();
        boolean syncSkipped = false;
        if (syncLatest && agenticProps.serviceConfigured() && agenticProps.enabled()) {
            syncSkipped = connectionRepository
                    .findByOwnerUserId(ownerUserId)
                    .map(conn -> !agenticService.syncConnectionBestEffort(conn))
                    .orElse(false);
        }
        RobinhoodRhDailyCaptureResultDto result = selfProvider
                .getObject()
                .captureManualSnapshotsForOwner(ownerUserId, Instant.now());
        if (!syncSkipped) {
            return result;
        }
        String note = " Live sync skipped — robinhood-agent sidecar unreachable; captured cached holdings.";
        return new RobinhoodRhDailyCaptureResultDto(
                result.ok(), result.capturedAt(), result.accountsCaptured(), result.message() + note);
    }

    /** Called by scheduled job — no HTTP user context. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RobinhoodRhDailyCaptureResultDto captureScheduledSnapshotsForOwner(long ownerUserId, Instant snapshotAt) {
        return captureForOwner(ownerUserId, snapshotAt, RobinhoodRhDailyCaptureKind.SCHEDULED, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RobinhoodRhDailyCaptureResultDto captureManualSnapshotsForOwner(long ownerUserId, Instant snapshotAt) {
        return captureForOwner(ownerUserId, snapshotAt, RobinhoodRhDailyCaptureKind.MANUAL, true);
    }

    private RobinhoodRhDailyCaptureResultDto captureForOwner(
            long ownerUserId, Instant snapshotAt, String captureKind, boolean syncLatest) {
        if (!accountTrackerConfigService.isDailyTrackerEnabled(ownerUserId)) {
            return new RobinhoodRhDailyCaptureResultDto(
                    false,
                    snapshotAt,
                    0,
                    "Daily Tracker is not enabled for your account.");
        }
        RobinhoodRhAccountsTrackDto track = rhAccountsTrackService.buildForOwner(ownerUserId, syncLatest);
        LocalDate snapshotDate = snapshotAt.atZone(CENTRAL).toLocalDate();
        Instant now = Instant.now();
        int captured = 0;
        boolean manual = RobinhoodRhDailyCaptureKind.MANUAL.equals(captureKind);

        for (RobinhoodRhAccountSummaryDto acct : track.accounts()) {
            String suffix = acct.accountSuffix();
            if (suffix == null || suffix.isBlank() || isHiddenAccount(ownerUserId, suffix)) {
                continue;
            }

            Optional<RobinhoodRhDailySnapshot> prevOpt = manual
                    ? snapshotRepository.findTopByOwnerUserIdAndAccountSuffixAndSnapshotAtLessThanOrderBySnapshotAtDesc(
                            ownerUserId, suffix, snapshotAt)
                    : snapshotRepository
                            .findTopByOwnerUserIdAndAccountSuffixAndCaptureKindAndSnapshotDateLessThanOrderBySnapshotDateDesc(
                                    ownerUserId, suffix, RobinhoodRhDailyCaptureKind.SCHEDULED, snapshotDate);

            LocalDate periodStartDate = prevOpt.map(RobinhoodRhDailySnapshot::getSnapshotDate).orElse(null);
            BigDecimal prevTotal = prevOpt
                    .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                    .orElse(nullToZero(acct.startingTotalValue()));

            List<RobinhoodRhCashFlowEventDto> periodFlows =
                    flowsInPeriod(acct.cashFlowEvents(), periodStartDate, snapshotDate);
            BigDecimal periodAdded = sumFlowAmounts(periodFlows, true);
            BigDecimal periodRemoved = sumFlowAmounts(periodFlows, false);

            BigDecimal currentTotal = nullToZero(acct.totalAccountValue());
            BigDecimal valueChange =
                    currentTotal.subtract(prevTotal).subtract(periodAdded).add(periodRemoved);

            RobinhoodRhDailySnapshot snapshot = manual
                    ? new RobinhoodRhDailySnapshot()
                    : snapshotRepository
                            .findByOwnerUserIdAndSnapshotDateAndAccountSuffixAndCaptureKind(
                                    ownerUserId, snapshotDate, suffix, RobinhoodRhDailyCaptureKind.SCHEDULED)
                            .orElseGet(RobinhoodRhDailySnapshot::new);

            snapshot.setOwnerUserId(ownerUserId);
            snapshot.setSnapshotAt(snapshotAt);
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setCaptureKind(captureKind);
            snapshot.setAccountSuffix(suffix);
            snapshot.setAccountNumber(acct.accountNumberMasked());
            snapshot.setLabel(acct.label());
            snapshot.setAccountKind(acct.accountKind());
            snapshot.setTotalAccountValue(scaleMoney(currentTotal));
            snapshot.setCashBalance(scaleMoney(nullToZero(acct.cashBalance())));
            snapshot.setEquityMarketValue(scaleMoney(nullToZero(acct.equityMarketValue())));
            snapshot.setPeriodAdded(scaleMoney(periodAdded));
            snapshot.setPeriodRemoved(scaleMoney(periodRemoved));
            snapshot.setPeriodValueChange(scaleMoney(valueChange));
            snapshot.setPeriodStartDate(periodStartDate);
            snapshot.setHoldingsJson(writeJson(acct.holdings()));
            snapshot.setFlowsJson(writeJson(periodFlows));
            if (snapshot.getCreatedAt() == null) {
                snapshot.setCreatedAt(now);
            }
            snapshotRepository.save(snapshot);
            captured++;
        }

        String message;
        if (captured == 0) {
            message = buildNoAccountsCaptureMessage(ownerUserId, track);
        } else if (manual) {
            message = "Saved manual capture at "
                    + MANUAL_TIME.format(snapshotAt)
                    + " Central ("
                    + captured
                    + " account"
                    + (captured == 1 ? "" : "s")
                    + ").";
            if (isScheduledCaptureOwner(ownerUserId)) {
                message += " The daily 9 PM row is unchanged.";
            }
        } else {
            message = "Captured " + captured + " scheduled snapshot(s) for " + snapshotDate + " Central.";
        }
        log.info("RH {} snapshot for user {}: {}", captureKind, ownerUserId, message);
        return new RobinhoodRhDailyCaptureResultDto(true, snapshotAt, captured, message);
    }

    private List<RobinhoodRhDailyTrackerManualCaptureDto> buildManualCapturesForDay(
            List<RobinhoodRhDailySnapshot> dayManualRows) {
        if (dayManualRows.isEmpty()) {
            return List.of();
        }
        Map<Instant, List<RobinhoodRhDailySnapshot>> byInstant = new TreeMap<>(Comparator.reverseOrder());
        for (RobinhoodRhDailySnapshot row : dayManualRows) {
            byInstant.computeIfAbsent(row.getSnapshotAt(), k -> new ArrayList<>()).add(row);
        }

        List<RobinhoodRhDailyTrackerManualCaptureDto> out = new ArrayList<>();
        for (Map.Entry<Instant, List<RobinhoodRhDailySnapshot>> entry : byInstant.entrySet()) {
            List<RobinhoodRhDailySnapshot> batch = entry.getValue();
            batch.sort(Comparator.comparing(RobinhoodRhDailySnapshot::getAccountSuffix));
            BigDecimal combined = batch.stream()
                    .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                    .map(RobinhoodRhDailyTrackerService::nullToZero)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<RobinhoodRhDailyTrackerManualCaptureAccountDto> accounts = batch.stream()
                    .map(r -> new RobinhoodRhDailyTrackerManualCaptureAccountDto(
                            r.getId(),
                            r.getAccountSuffix(),
                            r.getLabel(),
                            scaleMoney(r.getTotalAccountValue())))
                    .toList();
            out.add(new RobinhoodRhDailyTrackerManualCaptureDto(entry.getKey(), scaleMoney(combined), accounts));
        }
        return out;
    }

    private RobinhoodRhDailySnapshotDetailDto toDetailDto(RobinhoodRhDailySnapshot row) {
        List<RobinhoodRhHoldingDto> holdings = readJson(row.getHoldingsJson(), new TypeReference<>() {});
        holdings = rhAccountsTrackService.finalizeSnapshotHoldings(
                row.getOwnerUserId(), row.getAccountSuffix(), holdings, row.getEquityMarketValue());
        List<RobinhoodRhCashFlowEventDto> flows = readJson(row.getFlowsJson(), new TypeReference<>() {});
        return new RobinhoodRhDailySnapshotDetailDto(
                row.getId(),
                row.getSnapshotDate(),
                row.getSnapshotAt(),
                row.getCaptureKind(),
                row.getPeriodStartDate(),
                row.getAccountSuffix(),
                row.getLabel(),
                row.getAccountKind(),
                scaleMoney(row.getTotalAccountValue()),
                scaleMoney(row.getCashBalance()),
                scaleMoney(row.getEquityMarketValue()),
                scaleMoney(row.getPeriodAdded()),
                scaleMoney(row.getPeriodRemoved()),
                scaleMoney(row.getPeriodValueChange()),
                holdings,
                flows);
    }

    private String buildNoAccountsCaptureMessage(long ownerUserId, RobinhoodRhAccountsTrackDto track) {
        if (connectionRepository.findByOwnerUserId(ownerUserId).isEmpty()) {
            return "No Agentic Trading connection — connect your Robinhood Agentic profile first, then Sync now and Capture now.";
        }
        Set<String> profileSuffixes = accountTrackerConfigService.dailyTrackerProfileSuffixes(ownerUserId);
        LinkedHashSet<String> syncedSuffixes = new LinkedHashSet<>();
        for (RobinhoodRhAccountSummaryDto acct : track.accounts()) {
            if (acct.accountSuffix() != null && !acct.accountSuffix().isBlank()) {
                syncedSuffixes.add(acct.accountSuffix().trim());
            }
        }
        if (syncedSuffixes.isEmpty()) {
            return "No synced Robinhood accounts yet — open Agentic Trading, click Sync now, then Capture now.";
        }
        if (!profileSuffixes.isEmpty()) {
            boolean anyProfileMatch = syncedSuffixes.stream().anyMatch(profileSuffixes::contains);
            if (!anyProfileMatch) {
                return "Synced accounts (••••"
                        + String.join(", ••••", syncedSuffixes)
                        + ") do not match your Daily Tracker profile (••••"
                        + String.join(", ••••", profileSuffixes)
                        + "). Reconnect the correct Agentic profile and Sync now.";
            }
        }
        return "No Daily Tracker accounts to snapshot — sync holdings from your Agentic profile, then try Capture now again.";
    }

    private List<RobinhoodRhDailySnapshot> visibleSnapshots(long ownerUserId, List<RobinhoodRhDailySnapshot> rows) {
        return rows.stream()
                .filter(r -> !isHiddenAccount(ownerUserId, r.getAccountSuffix()))
                .toList();
    }

    private boolean isHiddenAccount(long ownerUserId, String suffix) {
        return !isDailyTrackerAccount(ownerUserId, suffix);
    }

    /** See {@link RobinhoodAccountTrackerConfigService#isDailyTrackerSuffix}. */
    private boolean isDailyTrackerAccount(long ownerUserId, String suffix) {
        return accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, suffix);
    }

    private static List<RobinhoodRhDailySnapshot> scheduledOnly(List<RobinhoodRhDailySnapshot> rows) {
        return rows.stream()
                .filter(r -> RobinhoodRhDailyCaptureKind.SCHEDULED.equals(r.getCaptureKind()))
                .toList();
    }

    private static List<RobinhoodRhDailySnapshot> manualOnly(List<RobinhoodRhDailySnapshot> rows) {
        return rows.stream()
                .filter(r -> RobinhoodRhDailyCaptureKind.MANUAL.equals(r.getCaptureKind()))
                .toList();
    }

    private static List<RobinhoodRhCashFlowEventDto> flowsInPeriod(
            List<RobinhoodRhCashFlowEventDto> all, LocalDate periodStartExclusive, LocalDate periodEndInclusive) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<RobinhoodRhCashFlowEventDto> out = new ArrayList<>();
        for (RobinhoodRhCashFlowEventDto event : all) {
            if ("STARTING_BALANCE".equals(event.flowCategory())) {
                continue;
            }
            if (event.activityDate() == null) {
                continue;
            }
            if (periodStartExclusive != null
                    && (event.activityDate().isBefore(periodStartExclusive)
                            || event.activityDate().equals(periodStartExclusive))) {
                continue;
            }
            if (event.activityDate().isAfter(periodEndInclusive)) {
                continue;
            }
            out.add(event);
        }
        return out;
    }

    private static BigDecimal sumFlowAmounts(List<RobinhoodRhCashFlowEventDto> flows, boolean added) {
        BigDecimal total = BigDecimal.ZERO;
        for (RobinhoodRhCashFlowEventDto flow : flows) {
            if ("IN".equals(flow.direction()) && added) {
                total = total.add(nullToZero(flow.amount()));
            } else if ("OUT".equals(flow.direction()) && !added) {
                total = total.add(nullToZero(flow.amount()));
            }
        }
        return total;
    }

    private static BigDecimal latestCombinedTotal(List<RobinhoodRhDailySnapshot> rows) {
        if (rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LocalDate latest = rows.stream()
                .map(RobinhoodRhDailySnapshot::getSnapshotDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latest == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = rows.stream()
                .filter(r -> latest.equals(r.getSnapshotDate()))
                .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                .map(RobinhoodRhDailyTrackerService::nullToZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return scaleMoney(sum);
    }

    private static BigDecimal combinedChange(List<RobinhoodRhDailySnapshot> rows) {
        if (rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LocalDate earliest = rows.stream()
                .map(RobinhoodRhDailySnapshot::getSnapshotDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate latest = rows.stream()
                .map(RobinhoodRhDailySnapshot::getSnapshotDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (earliest == null || latest == null || earliest.equals(latest)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal start = rows.stream()
                .filter(r -> earliest.equals(r.getSnapshotDate()))
                .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                .map(RobinhoodRhDailyTrackerService::nullToZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal end = rows.stream()
                .filter(r -> latest.equals(r.getSnapshotDate()))
                .map(RobinhoodRhDailySnapshot::getTotalAccountValue)
                .map(RobinhoodRhDailyTrackerService::nullToZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return scaleMoney(end.subtract(start));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            log.warn("Could not serialize snapshot JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Could not parse snapshot JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
