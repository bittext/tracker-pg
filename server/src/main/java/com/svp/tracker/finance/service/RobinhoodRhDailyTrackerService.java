package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticSyncedOrder;
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
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerPriorPullAccountDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerPriorPullDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerReportDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSyncedOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailyDayNoteRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final RobinhoodAgenticSyncedOrderRepository syncedOrderRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodRhDailyDayNoteRepository dayNoteRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhDailyTrackerProperties dailyTrackerProps;
    private final ObjectProvider<RobinhoodRhDailyTrackerService> selfProvider;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** Whether nightly scheduled snapshots and 9 PM schedule copy apply to this owner. */
    public boolean isScheduledCaptureOwner(long ownerUserId) {
        return appUserRepository
                .findById(ownerUserId)
                .map(u -> SCHEDULED_CAPTURE_OWNER_USERNAME.equalsIgnoreCase(u.getUsername().trim()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public RobinhoodRhDailyTrackerReportDto buildReport(int year, List<Integer> months) {
        long ownerUserId = currentUser.requireUserId();
        boolean scheduledOwner = isScheduledCaptureOwner(ownerUserId);
        List<RobinhoodAgenticSyncedOrder> ownerOrders =
                syncedOrderRepository.findByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(ownerUserId);
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<RobinhoodRhDailySnapshot> allYearRows =
                visibleSnapshots(
                        ownerUserId,
                        snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                                ownerUserId, yearStart, yearEnd));

        List<RobinhoodRhDailySnapshot> scheduledYearRows = scheduledOnly(allYearRows);
        List<RobinhoodRhDailySnapshot> intradayYearRows = intradayOnly(allYearRows);
        List<RobinhoodRhDailySnapshot> manualYearRows = manualOnly(allYearRows);

        Set<Integer> monthFilter = months == null || months.isEmpty() ? null : new HashSet<>(months);

        List<RobinhoodRhDailySnapshot> scheduledRows = scheduledYearRows.stream()
                .filter(r -> matchesMonthFilter(r.getSnapshotDate(), monthFilter))
                .toList();
        List<RobinhoodRhDailySnapshot> intradayRows = intradayYearRows.stream()
                .filter(r -> matchesMonthFilter(r.getSnapshotDate(), monthFilter))
                .toList();
        List<RobinhoodRhDailySnapshot> manualRows = manualYearRows.stream()
                .filter(r -> matchesMonthFilter(r.getSnapshotDate(), monthFilter))
                .toList();

        LinkedHashSet<String> suffixOrder = new LinkedHashSet<>();
        Map<String, RobinhoodRhDailyTrackerAccountColumnDto> columnBySuffix = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot row : allYearRows) {
            if (!matchesMonthFilter(row.getSnapshotDate(), monthFilter)) {
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

        Map<LocalDate, List<RobinhoodRhDailySnapshot>> intradayByDate = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot row : intradayRows) {
            intradayByDate.computeIfAbsent(row.getSnapshotDate(), k -> new ArrayList<>()).add(row);
        }

        Set<LocalDate> dayDates = new TreeSet<>(Comparator.reverseOrder());
        dayDates.addAll(scheduledByDate.keySet());
        dayDates.addAll(intradayByDate.keySet());
        dayDates.addAll(manualByDate.keySet());

        Map<LocalDate, List<RobinhoodRhDailySnapshot>> yearScheduledByDate = new TreeMap<>();
        for (RobinhoodRhDailySnapshot row : scheduledYearRows) {
            yearScheduledByDate.computeIfAbsent(row.getSnapshotDate(), k -> new ArrayList<>()).add(row);
        }
        TreeSet<LocalDate> allYearScheduledDates = new TreeSet<>(yearScheduledByDate.keySet());
        Map<LocalDate, BigDecimal> yearCombinedTotalByScheduledDate = new LinkedHashMap<>();
        Map<LocalDate, Map<String, BigDecimal>> yearAccountTotalByScheduledDate = new LinkedHashMap<>();
        for (LocalDate scheduledDate : allYearScheduledDates) {
            BigDecimal combined = BigDecimal.ZERO;
            Map<String, BigDecimal> bySuffix = new LinkedHashMap<>();
            for (RobinhoodRhDailySnapshot row : yearScheduledByDate.get(scheduledDate)) {
                BigDecimal total = nullToZero(row.getTotalAccountValue());
                combined = combined.add(total);
                bySuffix.put(row.getAccountSuffix(), total);
            }
            yearCombinedTotalByScheduledDate.put(scheduledDate, combined);
            yearAccountTotalByScheduledDate.put(scheduledDate, bySuffix);
        }

        TreeSet<LocalDate> scheduledDates = new TreeSet<>(scheduledByDate.keySet());
        Map<LocalDate, BigDecimal> combinedTotalByScheduledDate = new LinkedHashMap<>();
        Map<LocalDate, Map<String, BigDecimal>> accountTotalByScheduledDate = new LinkedHashMap<>();
        for (LocalDate scheduledDate : scheduledDates) {
            BigDecimal combined = BigDecimal.ZERO;
            Map<String, BigDecimal> bySuffix = new LinkedHashMap<>();
            for (RobinhoodRhDailySnapshot row : scheduledByDate.get(scheduledDate)) {
                BigDecimal total = nullToZero(row.getTotalAccountValue());
                combined = combined.add(total);
                bySuffix.put(row.getAccountSuffix(), total);
            }
            combinedTotalByScheduledDate.put(scheduledDate, combined);
            accountTotalByScheduledDate.put(scheduledDate, bySuffix);
        }

        Map<LocalDate, String> summaryNotesByDate = new LinkedHashMap<>();
        for (RobinhoodRhDailyDayNote noteRow :
                dayNoteRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
                        ownerUserId, yearStart, yearEnd)) {
            if (!matchesMonthFilter(noteRow.getSnapshotDate(), monthFilter)) {
                continue;
            }
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

            RobinhoodRhDailyTrackerPriorPullDto priorPull = buildPriorPullBeforeDay(dayDate, allYearRows);
            boolean hasPriorPull = priorPull != null;
            LocalDate previousScheduledDate = allYearScheduledDates.lower(dayDate);
            Map<String, BigDecimal> previousAccountTotals =
                    previousScheduledDate == null
                            ? Map.of()
                            : yearAccountTotalByScheduledDate.getOrDefault(previousScheduledDate, Map.of());

            for (RobinhoodRhDailySnapshot row : dayScheduled) {
                combinedTotal = combinedTotal.add(nullToZero(row.getTotalAccountValue()));
                combinedAdded = combinedAdded.add(nullToZero(row.getPeriodAdded()));
                combinedRemoved = combinedRemoved.add(nullToZero(row.getPeriodRemoved()));
                combinedValueChange = combinedValueChange.add(nullToZero(row.getPeriodValueChange()));
                boolean flowActivity =
                        row.getPeriodAdded().signum() != 0 || row.getPeriodRemoved().signum() != 0;
                List<RobinhoodRhDailyTradeDto> rowTrades = resolveTradesForSnapshot(row, ownerOrders);
                for (RobinhoodRhDailyTradeDto trade : rowTrades) {
                    dayTrades.add(trade.withAccount(row.getAccountSuffix(), row.getLabel()));
                }
                BigDecimal accountTotalChange = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                if (previousScheduledDate != null) {
                    accountTotalChange = scaleMoney(nullToZero(row.getTotalAccountValue())
                            .subtract(nullToZero(previousAccountTotals.get(row.getAccountSuffix()))));
                }
                cells.add(new RobinhoodRhDailyTrackerAccountCellDto(
                        row.getId(),
                        row.getAccountSuffix(),
                        scaleMoney(row.getTotalAccountValue()),
                        accountTotalChange,
                        scaleMoney(row.getPeriodAdded()),
                        scaleMoney(row.getPeriodRemoved()),
                        scaleMoney(row.getPeriodValueChange()),
                        flowActivity,
                        rowTrades.size(),
                        positionsChangedFromPrior(ownerUserId, row, allYearRows)));
            }

            List<RobinhoodRhDailyTradeDto> dayTrades = buildDayTrades(
                    dayDate,
                    dayScheduled,
                    intradayByDate.getOrDefault(dayDate, List.of()),
                    manualByDate.getOrDefault(dayDate, List.of()),
                    previousScheduledDate,
                    ownerOrders,
                    columnBySuffix);

            boolean hasPreviousScheduledSnapshot =
                    previousScheduledDate != null && !dayScheduled.isEmpty();
            BigDecimal combinedTotalChangeFromPrevious = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (hasPreviousScheduledSnapshot) {
                combinedTotalChangeFromPrevious = scaleMoney(combinedTotal.subtract(
                        yearCombinedTotalByScheduledDate.getOrDefault(previousScheduledDate, BigDecimal.ZERO)));
            }

            List<RobinhoodRhDailyTrackerManualCaptureDto> intradayCaptures =
                    buildCapturesGroupedByInstant(
                            ownerUserId, intradayByDate.getOrDefault(dayDate, List.of()), allYearRows);
            List<RobinhoodRhDailyTrackerManualCaptureDto> manualCaptures =
                    buildCapturesGroupedByInstant(
                            ownerUserId, manualByDate.getOrDefault(dayDate, List.of()), allYearRows);

            days.add(new RobinhoodRhDailyTrackerDayDto(
                    dayDate,
                    snapshotAt,
                    !dayScheduled.isEmpty(),
                    scaleMoney(combinedTotal),
                    combinedTotalChangeFromPrevious,
                    hasPreviousScheduledSnapshot,
                    scaleMoney(combinedAdded),
                    scaleMoney(combinedRemoved),
                    scaleMoney(combinedValueChange),
                    priorPull,
                    hasPriorPull,
                    cells,
                    intradayCaptures,
                    manualCaptures,
                    dayTrades,
                    summaryNotesByDate.getOrDefault(dayDate, "")));
        }

        List<RobinhoodRhDailySnapshot> monthScheduledRows = scheduledYearRows.stream()
                .filter(r -> matchesMonthFilter(r.getSnapshotDate(), monthFilter))
                .toList();

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
                    "Each day shows the scheduled 9 PM snapshot plus hourly pulls under Captures today (Timeline view).");
            notes.add("Period flows on scheduled rows are cash movements since the previous 9 PM snapshot.");
            if (days.isEmpty()) {
                notes.add(
                        "No snapshots yet — wait for the hourly job or click Capture now after connecting pulickal-agentic.");
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
        Integer singleMonth = months != null && months.size() == 1 ? months.get(0) : null;
        List<Integer> responseMonths = months == null ? List.of() : List.copyOf(months);
        return new RobinhoodRhDailyTrackerReportDto(
                year,
                singleMonth,
                responseMonths,
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
    public RobinhoodRhDailyCaptureResultDto captureIntradaySnapshotsForOwner(long ownerUserId, Instant snapshotAt) {
        return captureForOwner(ownerUserId, snapshotAt, RobinhoodRhDailyCaptureKind.INTRADAY, false);
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
        boolean pointInTime = isPointInTimeCaptureKind(captureKind);
        List<RobinhoodAgenticSyncedOrder> ownerOrders =
                syncedOrderRepository.findByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(ownerUserId);

        for (RobinhoodRhAccountSummaryDto acct : track.accounts()) {
            String suffix = acct.accountSuffix();
            if (suffix == null || suffix.isBlank() || isHiddenAccount(ownerUserId, suffix)) {
                continue;
            }

            Optional<RobinhoodRhDailySnapshot> prevOpt = pointInTime
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

            RobinhoodRhDailySnapshot snapshot = pointInTime
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
            snapshot.setTradesJson(writeJson(tradesInPeriod(ownerOrders, suffix, periodStartDate, snapshotDate)));
            if (snapshot.getCreatedAt() == null) {
                snapshot.setCreatedAt(now);
            }
            snapshotRepository.save(snapshot);
            captured++;
        }

        String message;
        if (captured == 0) {
            message = buildNoAccountsCaptureMessage(ownerUserId, track);
        } else if (RobinhoodRhDailyCaptureKind.MANUAL.equals(captureKind)) {
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
        } else if (RobinhoodRhDailyCaptureKind.INTRADAY.equals(captureKind)) {
            message = "Saved hourly capture at "
                    + MANUAL_TIME.format(snapshotAt)
                    + " Central ("
                    + captured
                    + " account"
                    + (captured == 1 ? "" : "s")
                    + ").";
        } else {
            message = "Captured " + captured + " scheduled snapshot(s) for " + snapshotDate + " Central.";
        }
        log.info("RH {} snapshot for user {}: {}", captureKind, ownerUserId, message);
        return new RobinhoodRhDailyCaptureResultDto(true, snapshotAt, captured, message);
    }

    private RobinhoodRhDailyTrackerPriorPullDto buildPriorPullBeforeDay(
            LocalDate dayDate, List<RobinhoodRhDailySnapshot> allYearRows) {
        Instant dayStart = dayDate.atStartOfDay(CENTRAL).toInstant();
        Map<Instant, List<RobinhoodRhDailySnapshot>> byInstant = new TreeMap<>(Comparator.reverseOrder());
        for (RobinhoodRhDailySnapshot row : allYearRows) {
            Instant at = row.getSnapshotAt();
            if (at == null || !at.isBefore(dayStart)) {
                continue;
            }
            byInstant.computeIfAbsent(at, k -> new ArrayList<>()).add(row);
        }
        if (byInstant.isEmpty()) {
            return null;
        }
        List<RobinhoodRhDailySnapshot> batch = new ArrayList<>(byInstant.values().iterator().next());
        batch.sort(Comparator.comparing(RobinhoodRhDailySnapshot::getAccountSuffix));
        BigDecimal combined = BigDecimal.ZERO;
        List<RobinhoodRhDailyTrackerPriorPullAccountDto> accounts = new ArrayList<>();
        for (RobinhoodRhDailySnapshot row : batch) {
            combined = combined.add(nullToZero(row.getTotalAccountValue()));
            accounts.add(new RobinhoodRhDailyTrackerPriorPullAccountDto(
                    row.getAccountSuffix(), scaleMoney(row.getTotalAccountValue())));
        }
        RobinhoodRhDailySnapshot sample = batch.get(0);
        return new RobinhoodRhDailyTrackerPriorPullDto(
                sample.getSnapshotDate(),
                sample.getSnapshotAt(),
                sample.getCaptureKind(),
                scaleMoney(combined),
                List.copyOf(accounts));
    }

    private List<RobinhoodRhDailyTrackerManualCaptureDto> buildCapturesGroupedByInstant(
            long ownerUserId,
            List<RobinhoodRhDailySnapshot> dayRows,
            List<RobinhoodRhDailySnapshot> allYearRows) {
        if (dayRows.isEmpty()) {
            return List.of();
        }
        Map<Instant, List<RobinhoodRhDailySnapshot>> byInstant = new TreeMap<>(Comparator.reverseOrder());
        for (RobinhoodRhDailySnapshot row : dayRows) {
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
                            scaleMoney(r.getTotalAccountValue()),
                            positionsChangedFromPrior(ownerUserId, r, allYearRows)))
                    .toList();
            out.add(new RobinhoodRhDailyTrackerManualCaptureDto(entry.getKey(), scaleMoney(combined), accounts));
        }
        return out;
    }

    private RobinhoodRhDailySnapshotDetailDto toDetailDto(RobinhoodRhDailySnapshot row) {
        // A snapshot is a point-in-time record: show the prices captured at snapshot time.
        // Re-pricing with live quotes here would corrupt historical rows and diverge from the
        // stored equity/total values, so the holdings JSON is deserialized as-is then legacy
        // option rows are normalized to per-share without live re-pricing.
        List<RobinhoodRhHoldingDto> holdings =
                RobinhoodRhHoldingValues.normalizeStoredSnapshotHoldings(
                        readJson(row.getHoldingsJson(), new TypeReference<>() {}));
        List<RobinhoodRhCashFlowEventDto> flows = readJson(row.getFlowsJson(), new TypeReference<>() {});
        List<RobinhoodAgenticSyncedOrder> ownerOrders =
                syncedOrderRepository.findByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(row.getOwnerUserId());
        List<RobinhoodRhDailyTradeDto> trades = resolveTradesForSnapshot(row, ownerOrders);
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
                flows,
                trades);
    }

    /**
     * Trades for the expanded day panel. Prefer the official {@code SCHEDULED} (9 PM) snapshots; when those
     * are missing or empty, fall back to stored trades on intraday/manual captures, then live synced orders
     * for the period since the prior scheduled day.
     */
    List<RobinhoodRhDailyTradeDto> buildDayTrades(
            LocalDate dayDate,
            List<RobinhoodRhDailySnapshot> dayScheduled,
            List<RobinhoodRhDailySnapshot> dayIntraday,
            List<RobinhoodRhDailySnapshot> dayManual,
            LocalDate previousScheduledDate,
            List<RobinhoodAgenticSyncedOrder> ownerOrders,
            Map<String, RobinhoodRhDailyTrackerAccountColumnDto> columnBySuffix) {
        List<RobinhoodRhDailyTradeDto> fromScheduled = tradesFromSnapshots(dayScheduled, ownerOrders);
        if (!fromScheduled.isEmpty()) {
            return fromScheduled;
        }

        List<RobinhoodRhDailySnapshot> pointInTime = new ArrayList<>(dayIntraday.size() + dayManual.size());
        pointInTime.addAll(dayIntraday);
        pointInTime.addAll(dayManual);
        List<RobinhoodRhDailyTradeDto> fromCaptures = tradesFromLatestCapturePerAccount(pointInTime, ownerOrders);
        if (!fromCaptures.isEmpty()) {
            return fromCaptures;
        }

        List<RobinhoodRhDailyTradeDto> fromOrders = new ArrayList<>();
        for (RobinhoodRhDailyTrackerAccountColumnDto column : columnBySuffix.values()) {
            for (RobinhoodRhDailyTradeDto trade :
                    tradesInPeriod(ownerOrders, column.accountSuffix(), previousScheduledDate, dayDate)) {
                fromOrders.add(trade.withAccount(column.accountSuffix(), column.label()));
            }
        }
        return dedupeTrades(fromOrders);
    }

    private List<RobinhoodRhDailyTradeDto> tradesFromSnapshots(
            List<RobinhoodRhDailySnapshot> rows, List<RobinhoodAgenticSyncedOrder> ownerOrders) {
        List<RobinhoodRhDailyTradeDto> out = new ArrayList<>();
        for (RobinhoodRhDailySnapshot row : rows) {
            for (RobinhoodRhDailyTradeDto trade : resolveTradesForSnapshot(row, ownerOrders)) {
                out.add(trade.withAccount(row.getAccountSuffix(), row.getLabel()));
            }
        }
        return dedupeTrades(out);
    }

    private List<RobinhoodRhDailyTradeDto> tradesFromLatestCapturePerAccount(
            List<RobinhoodRhDailySnapshot> rows, List<RobinhoodAgenticSyncedOrder> ownerOrders) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, RobinhoodRhDailySnapshot> latestBySuffix = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot row : rows) {
            latestBySuffix.merge(
                    row.getAccountSuffix(),
                    row,
                    (left, right) -> {
                        Instant leftAt = left.getSnapshotAt();
                        Instant rightAt = right.getSnapshotAt();
                        if (leftAt == null) {
                            return right;
                        }
                        if (rightAt == null) {
                            return left;
                        }
                        return rightAt.isAfter(leftAt) ? right : left;
                    });
        }
        return tradesFromSnapshots(new ArrayList<>(latestBySuffix.values()), ownerOrders);
    }

    private static List<RobinhoodRhDailyTradeDto> dedupeTrades(List<RobinhoodRhDailyTradeDto> trades) {
        Map<String, RobinhoodRhDailyTradeDto> unique = new LinkedHashMap<>();
        for (RobinhoodRhDailyTradeDto trade : trades) {
            String key = String.join(
                    "\u0000",
                    trade.accountSuffix() == null ? "" : trade.accountSuffix(),
                    trade.symbol() == null ? "" : trade.symbol(),
                    trade.side() == null ? "" : trade.side(),
                    trade.executedAt() == null ? "" : trade.executedAt().toString());
            unique.putIfAbsent(key, trade);
        }
        List<RobinhoodRhDailyTradeDto> out = new ArrayList<>(unique.values());
        out.sort(Comparator.comparing(
                RobinhoodRhDailyTradeDto::executedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private List<RobinhoodRhDailyTradeDto> resolveTradesForSnapshot(
            RobinhoodRhDailySnapshot row, List<RobinhoodAgenticSyncedOrder> ownerOrders) {
        List<RobinhoodRhDailyTradeDto> stored = readJson(row.getTradesJson(), new TypeReference<>() {});
        if (!stored.isEmpty()) {
            return stored;
        }
        return tradesInPeriod(ownerOrders, row.getAccountSuffix(), row.getPeriodStartDate(), row.getSnapshotDate());
    }

    /**
     * Executed trades for one account within the snapshot period (period start exclusive → snapshot date inclusive).
     * Only filled/executed orders count; open/queued/cancelled states are excluded.
     */
    private List<RobinhoodRhDailyTradeDto> tradesInPeriod(
            List<RobinhoodAgenticSyncedOrder> orders,
            String accountSuffix,
            LocalDate periodStartExclusive,
            LocalDate periodEndInclusive) {
        if (orders == null || orders.isEmpty() || accountSuffix == null) {
            return List.of();
        }
        String suffix = accountSuffix.trim();
        List<RobinhoodRhDailyTradeDto> out = new ArrayList<>();
        for (RobinhoodAgenticSyncedOrder order : orders) {
            if (!suffix.equals(lastFour(order.getAccountNumber()))) {
                continue;
            }
            if (!isExecutedTrade(order.getState())) {
                continue;
            }
            Instant executedAt = order.getUpdatedAtRh() != null ? order.getUpdatedAtRh() : order.getCreatedAtRh();
            if (executedAt == null) {
                continue;
            }
            LocalDate tradeDate = executedAt.atZone(CENTRAL).toLocalDate();
            if (periodStartExclusive != null && !tradeDate.isAfter(periodStartExclusive)) {
                continue;
            }
            if (tradeDate.isAfter(periodEndInclusive)) {
                continue;
            }
            out.add(new RobinhoodRhDailyTradeDto(
                    order.getSymbol(),
                    order.getSide(),
                    order.getOrderType(),
                    order.getQuantity(),
                    order.getAveragePrice(),
                    order.getLimitPrice(),
                    order.getState(),
                    executedAt));
        }
        out.sort(Comparator.comparing(
                RobinhoodRhDailyTradeDto::executedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private static boolean isExecutedTrade(String state) {
        if (state == null) {
            return false;
        }
        String s = state.trim().toLowerCase(Locale.ROOT);
        return s.equals("filled")
                || s.equals("partially_filled")
                || s.equals("completed")
                || s.equals("executed")
                || s.contains("fill");
    }

    private static String lastFour(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String digits = accountNumber.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return null;
        }
        return digits.substring(digits.length() - 4);
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

    private static List<RobinhoodRhDailySnapshot> intradayOnly(List<RobinhoodRhDailySnapshot> rows) {
        return rows.stream()
                .filter(r -> RobinhoodRhDailyCaptureKind.INTRADAY.equals(r.getCaptureKind()))
                .toList();
    }

    private static boolean isPointInTimeCaptureKind(String captureKind) {
        return RobinhoodRhDailyCaptureKind.MANUAL.equals(captureKind)
                || RobinhoodRhDailyCaptureKind.INTRADAY.equals(captureKind);
    }

    private boolean positionsChangedFromPrior(
            long ownerUserId, RobinhoodRhDailySnapshot current, List<RobinhoodRhDailySnapshot> allRows) {
        String suffix = current.getAccountSuffix();
        if (suffix == null
                || RobinhoodRhDailyTrackerAccountPolicy.POSITION_CHANGE_HIGHLIGHT_EXCLUDED_SUFFIX.equals(
                        suffix.trim())) {
            return false;
        }
        Instant at = current.getSnapshotAt();
        if (at == null) {
            return false;
        }
        Optional<RobinhoodRhDailySnapshot> priorOpt =
                findPriorSnapshotForSuffix(allRows, ownerUserId, suffix.trim(), at);
        if (priorOpt.isEmpty()) {
            return false;
        }
        return holdingsPositionsChanged(priorOpt.get(), current);
    }

    private static Optional<RobinhoodRhDailySnapshot> findPriorSnapshotForSuffix(
            List<RobinhoodRhDailySnapshot> allRows, long ownerUserId, String suffix, Instant before) {
        return allRows.stream()
                .filter(r -> r.getOwnerUserId() == ownerUserId)
                .filter(r -> suffix.equals(r.getAccountSuffix()))
                .filter(r -> r.getSnapshotAt() != null && r.getSnapshotAt().isBefore(before))
                .max(Comparator.comparing(RobinhoodRhDailySnapshot::getSnapshotAt));
    }

    private boolean holdingsPositionsChanged(RobinhoodRhDailySnapshot prior, RobinhoodRhDailySnapshot current) {
        return !holdingsQuantityByPositionKey(prior).equals(holdingsQuantityByPositionKey(current));
    }

    private Map<String, BigDecimal> holdingsQuantityByPositionKey(RobinhoodRhDailySnapshot row) {
        List<RobinhoodRhHoldingDto> holdings = readJson(row.getHoldingsJson(), new TypeReference<>() {});
        if (holdings == null || holdings.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> quantities = new TreeMap<>();
        for (RobinhoodRhHoldingDto holding : holdings) {
            String key = holdingPositionKey(holding);
            if (key.isEmpty()) {
                continue;
            }
            BigDecimal qty = nullToZero(holding.quantity()).setScale(4, RoundingMode.HALF_UP);
            if (qty.signum() == 0) {
                continue;
            }
            quantities.merge(key, qty, BigDecimal::add);
        }
        return quantities;
    }

    private static String holdingPositionKey(RobinhoodRhHoldingDto holding) {
        if (holding == null || holding.symbol() == null || holding.symbol().isBlank()) {
            return "";
        }
        String symbol = holding.symbol().trim().toUpperCase(Locale.ROOT);
        String type = holding.positionType() == null || holding.positionType().isBlank()
                ? "STOCK"
                : holding.positionType().trim().toUpperCase(Locale.ROOT);
        return symbol + "|" + type;
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

    private static boolean matchesMonthFilter(LocalDate date, Set<Integer> monthFilter) {
        return monthFilter == null || monthFilter.contains(date.getMonthValue());
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
