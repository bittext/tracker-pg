package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RhDailyTrackerAlertEvent;
import com.svp.tracker.finance.domain.RobinhoodAgenticSyncedOrder;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.domain.RobinhoodRhDailyDayNote;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.RhDailyTrackerSnapshotAlertDto;
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
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerRefreshHintDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerReportDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.TradingJournalCalendarDayDto;
import com.svp.tracker.finance.repository.RhDailyTrackerAlertEventRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSyncedOrderRepository;
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
import java.util.HashSet;
import java.util.HashMap;
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
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Daily Robinhood account snapshots for Reports → Daily Tracker (official close 9 PM Central). */
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
    private static final long DAY_WRAP_CACHE_TTL_MS = 120_000L;

    private final CurrentUserService currentUser;
    private final AppUserRepository appUserRepository;
    private final RobinhoodRhAccountsTrackService rhAccountsTrackService;
    private final RobinhoodAgenticService agenticService;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticSyncedOrderRepository syncedOrderRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodRhDailyDayNoteRepository dayNoteRepository;
    private final RhDailyTrackerAlertEventRepository alertEventRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final Sp500DailyBenchmarkService sp500DailyBenchmarkService;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodRhDailyTrackerProperties dailyTrackerProps;
    private final ObjectProvider<RobinhoodRhDailyTrackerService> selfProvider;
    private final ObjectProvider<RobinhoodRhDailyTrackerAlertService> alertServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConcurrentHashMap<WrapCacheKey, CachedWrap> dayWrapCache = new ConcurrentHashMap<>();

    private record WrapCacheKey(long ownerUserId, LocalDate snapshotDate) {}

    private record CachedWrap(RobinhoodRhDailyTrackerDayDto wrap, long cachedAtMs) {}

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

        Set<Long> snapshotIds = new HashSet<>();
        for (RobinhoodRhDailySnapshot row : allYearRows) {
            snapshotIds.add(row.getId());
        }
        Map<Long, RhDailyTrackerAlertEvent> alertsBySnapshotId =
                loadSpikeAlertsBySnapshotId(ownerUserId, snapshotIds);

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
                        positionsChangedFromPrior(ownerUserId, row, allYearRows),
                        spikeAlertFor(alertsBySnapshotId, row.getId())));
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
                            ownerUserId,
                            intradayByDate.getOrDefault(dayDate, List.of()),
                            allYearRows,
                            alertsBySnapshotId);
            List<RobinhoodRhDailyTrackerManualCaptureDto> manualCaptures =
                    buildCapturesGroupedByInstant(
                            ownerUserId,
                            manualByDate.getOrDefault(dayDate, List.of()),
                            allYearRows,
                            alertsBySnapshotId);

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
                    "Each day shows the scheduled 9 PM CT snapshot plus hourly pulls under Capture pulse.");
            notes.add("Period flows on scheduled rows are cash movements since the previous 9 PM CT snapshot.");
            notes.add("After the 9 PM CT close, later hourly captures stay on the timeline but do not replace the day total.");
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
                sp500DailyBenchmarkService.alignedCloses(scheduledDates),
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

    /** Latest stored snapshot id/at for UI polling after scheduled or manual captures. */
    @Transactional(readOnly = true)
    public RobinhoodRhDailyTrackerRefreshHintDto refreshHint() {
        long ownerUserId = currentUser.requireUserId();
        return refreshHintForOwner(ownerUserId);
    }

    @Transactional(readOnly = true)
    public RobinhoodRhDailyTrackerRefreshHintDto refreshHintForOwner(long ownerUserId) {
        return snapshotRepository
                .findTopByOwnerUserIdOrderBySnapshotAtDescIdDesc(ownerUserId)
                .map(row -> new RobinhoodRhDailyTrackerRefreshHintDto(
                        row.getSnapshotAt(), row.getId(), row.getCaptureKind()))
                .orElseGet(() -> new RobinhoodRhDailyTrackerRefreshHintDto(null, 0L, ""));
    }

    /** Single-day wrap for Trading Journal — avoids rebuilding a full month Daily Tracker report. */
    @Transactional(readOnly = true)
    public RobinhoodRhDailyTrackerDayDto dayWrap(LocalDate snapshotDate) {
        if (snapshotDate == null) {
            return null;
        }
        long ownerUserId = currentUser.requireUserId();
        WrapCacheKey cacheKey = new WrapCacheKey(ownerUserId, snapshotDate);
        CachedWrap cached = dayWrapCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.cachedAtMs() < DAY_WRAP_CACHE_TTL_MS) {
            return cached.wrap();
        }

        List<RobinhoodRhDailySnapshot> dayRows = visibleSnapshots(
                ownerUserId,
                snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                        ownerUserId, snapshotDate, snapshotDate));
        if (dayRows.isEmpty()) {
            dayWrapCache.put(cacheKey, new CachedWrap(null, now));
            return null;
        }

        List<RobinhoodRhDailySnapshot> dayScheduled = new ArrayList<>(scheduledOnly(dayRows));
        dayScheduled.sort(Comparator.comparing(RobinhoodRhDailySnapshot::getAccountSuffix));
        List<RobinhoodRhDailySnapshot> dayIntraday = intradayOnly(dayRows);
        List<RobinhoodRhDailySnapshot> dayManual = manualOnly(dayRows);

        LocalDate lookbackFrom = snapshotDate.minusDays(45);
        List<RobinhoodRhDailySnapshot> priorScheduledRows = scheduledOnly(visibleSnapshots(
                ownerUserId,
                snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                        ownerUserId, lookbackFrom, snapshotDate.minusDays(1))));
        TreeMap<LocalDate, List<RobinhoodRhDailySnapshot>> priorByDate = new TreeMap<>();
        for (RobinhoodRhDailySnapshot row : priorScheduledRows) {
            priorByDate.computeIfAbsent(row.getSnapshotDate(), k -> new ArrayList<>()).add(row);
        }
        LocalDate previousScheduledDate = priorByDate.isEmpty() ? null : priorByDate.lastKey();
        Map<String, BigDecimal> previousAccountTotals = new LinkedHashMap<>();
        BigDecimal previousCombined = BigDecimal.ZERO;
        if (previousScheduledDate != null) {
            for (RobinhoodRhDailySnapshot row : priorByDate.get(previousScheduledDate)) {
                BigDecimal total = nullToZero(row.getTotalAccountValue());
                previousAccountTotals.put(row.getAccountSuffix(), total);
                previousCombined = previousCombined.add(total);
            }
        }

        List<RobinhoodAgenticSyncedOrder> ownerOrders = List.of();
        if (needsSyncedOrderFallback(dayScheduled, dayIntraday, dayManual)) {
            ownerOrders = syncedOrderRepository.findByOwnerUserIdOrderByUpdatedAtRhDescCreatedAtRhDesc(ownerUserId);
        }

        Instant snapshotAt = dayScheduled.stream()
                .map(RobinhoodRhDailySnapshot::getSnapshotAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        BigDecimal combinedTotal = BigDecimal.ZERO;
        BigDecimal combinedAdded = BigDecimal.ZERO;
        BigDecimal combinedRemoved = BigDecimal.ZERO;
        BigDecimal combinedValueChange = BigDecimal.ZERO;
        List<RobinhoodRhDailyTrackerAccountCellDto> cells = new ArrayList<>();
        LinkedHashMap<String, RobinhoodRhDailyTrackerAccountColumnDto> columnBySuffix = new LinkedHashMap<>();

        for (RobinhoodRhDailySnapshot row : dayScheduled) {
            combinedTotal = combinedTotal.add(nullToZero(row.getTotalAccountValue()));
            combinedAdded = combinedAdded.add(nullToZero(row.getPeriodAdded()));
            combinedRemoved = combinedRemoved.add(nullToZero(row.getPeriodRemoved()));
            combinedValueChange = combinedValueChange.add(nullToZero(row.getPeriodValueChange()));
            boolean flowActivity =
                    row.getPeriodAdded().signum() != 0 || row.getPeriodRemoved().signum() != 0;
            List<RobinhoodRhDailyTradeDto> rowTrades = resolveTradesForSnapshot(row, ownerOrders);
            BigDecimal accountTotalChange = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (previousScheduledDate != null) {
                accountTotalChange = scaleMoney(nullToZero(row.getTotalAccountValue())
                        .subtract(nullToZero(previousAccountTotals.get(row.getAccountSuffix()))));
            }
            columnBySuffix.putIfAbsent(
                    row.getAccountSuffix(),
                    new RobinhoodRhDailyTrackerAccountColumnDto(
                            row.getAccountSuffix(), row.getLabel(), row.getAccountKind()));
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
                    false,
                    RhDailyTrackerSnapshotAlertDto.none()));
        }

        for (RobinhoodRhDailySnapshot row : dayRows) {
            columnBySuffix.putIfAbsent(
                    row.getAccountSuffix(),
                    new RobinhoodRhDailyTrackerAccountColumnDto(
                            row.getAccountSuffix(), row.getLabel(), row.getAccountKind()));
        }

        List<RobinhoodRhDailyTradeDto> dayTrades = buildDayTrades(
                snapshotDate,
                dayScheduled,
                dayIntraday,
                dayManual,
                previousScheduledDate,
                ownerOrders,
                columnBySuffix);

        boolean hasPreviousScheduledSnapshot = previousScheduledDate != null && !dayScheduled.isEmpty();
        BigDecimal combinedTotalChangeFromPrevious = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (hasPreviousScheduledSnapshot) {
            combinedTotalChangeFromPrevious = scaleMoney(combinedTotal.subtract(previousCombined));
        }

        String summaryNote = dayNoteRepository
                .findByOwnerUserIdAndSnapshotDate(ownerUserId, snapshotDate)
                .map(RobinhoodRhDailyDayNote::getNoteText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("");

        RobinhoodRhDailyTrackerDayDto wrap = new RobinhoodRhDailyTrackerDayDto(
                snapshotDate,
                snapshotAt,
                !dayScheduled.isEmpty(),
                scaleMoney(combinedTotal),
                combinedTotalChangeFromPrevious,
                hasPreviousScheduledSnapshot,
                scaleMoney(combinedAdded),
                scaleMoney(combinedRemoved),
                scaleMoney(combinedValueChange),
                null,
                false,
                cells,
                List.of(),
                List.of(),
                dayTrades,
                summaryNote);
        dayWrapCache.put(cacheKey, new CachedWrap(wrap, now));
        return wrap;
    }

    /**
     * Lightweight month series of combined Δ vs prior scheduled 9 PM CT close — for Trading Journal
     * calendar heatmap (avoids full {@link #buildReport}).
     */
    @Transactional(readOnly = true)
    public List<TradingJournalCalendarDayDto> monthCloseChanges(int year, int month) {
        if (month < 1 || month > 12) {
            return List.of();
        }
        YearMonth ym = YearMonth.of(year, month);
        return closeChangesBetween(ym.atDay(1), ym.atEndOfMonth());
    }

    /** Full-year prior-close Δ series for Trading Journal “All months” year grid. */
    @Transactional(readOnly = true)
    public List<TradingJournalCalendarDayDto> yearCloseChanges(int year) {
        return closeChangesBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private List<TradingJournalCalendarDayDto> closeChangesBetween(LocalDate rangeStart, LocalDate rangeEnd) {
        if (rangeStart == null || rangeEnd == null || rangeEnd.isBefore(rangeStart)) {
            return List.of();
        }
        long ownerUserId = currentUser.requireUserId();
        LocalDate lookbackFrom = rangeStart.minusDays(45);

        List<RobinhoodRhDailySnapshot> scheduledRows = scheduledOnly(visibleSnapshots(
                ownerUserId,
                snapshotRepository.findByOwnerUserIdAndSnapshotDateBetweenOrderBySnapshotDateDescAccountSuffixAsc(
                        ownerUserId, lookbackFrom, rangeEnd)));

        TreeMap<LocalDate, BigDecimal> combinedByDate = new TreeMap<>();
        for (RobinhoodRhDailySnapshot row : scheduledRows) {
            combinedByDate.merge(row.getSnapshotDate(), nullToZero(row.getTotalAccountValue()), BigDecimal::add);
        }
        if (combinedByDate.isEmpty()) {
            return List.of();
        }

        List<TradingJournalCalendarDayDto> out = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : combinedByDate.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isBefore(rangeStart) || date.isAfter(rangeEnd)) {
                continue;
            }
            LocalDate previous = combinedByDate.lowerKey(date);
            boolean hasPrevious = previous != null;
            BigDecimal change = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (hasPrevious) {
                change = scaleMoney(entry.getValue().subtract(combinedByDate.get(previous)));
            }
            out.add(new TradingJournalCalendarDayDto(date, change, hasPrevious));
        }
        return out;
    }

    private boolean needsSyncedOrderFallback(
            List<RobinhoodRhDailySnapshot> dayScheduled,
            List<RobinhoodRhDailySnapshot> dayIntraday,
            List<RobinhoodRhDailySnapshot> dayManual) {
        if (!tradesFromSnapshots(dayScheduled, List.of()).isEmpty()) {
            return false;
        }
        List<RobinhoodRhDailySnapshot> pointInTime = new ArrayList<>(dayIntraday.size() + dayManual.size());
        pointInTime.addAll(dayIntraday);
        pointInTime.addAll(dayManual);
        return tradesFromLatestCapturePerAccount(pointInTime, List.of()).isEmpty();
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
        List<RobinhoodRhDailySnapshot> savedSnapshots = new ArrayList<>();
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
            savedSnapshots.add(snapshot);
            captured++;
        }

        if (captured > 0) {
            alertServiceProvider.getObject().evaluateAfterCapture(ownerUserId, savedSnapshots);
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
                message += " The daily 9 PM CT row is unchanged.";
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
            message = "Captured " + captured + " scheduled 9 PM CT snapshot(s) for " + snapshotDate + " (Central date).";
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
            List<RobinhoodRhDailySnapshot> allYearRows,
            Map<Long, RhDailyTrackerAlertEvent> alertsBySnapshotId) {
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
                            positionsChangedFromPrior(ownerUserId, r, allYearRows),
                            spikeAlertFor(alertsBySnapshotId, r.getId())))
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

    private Map<Long, RhDailyTrackerAlertEvent> loadSpikeAlertsBySnapshotId(
            long ownerUserId, Set<Long> snapshotIds) {
        if (snapshotIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RhDailyTrackerAlertEvent> out = new HashMap<>();
        for (RhDailyTrackerAlertEvent event :
                alertEventRepository.findByOwnerUserIdAndSnapshotIdIn(ownerUserId, snapshotIds)) {
            if (event.getSnapshotId() == null || "TEST".equals(event.getTriggerReasons())) {
                continue;
            }
            out.putIfAbsent(event.getSnapshotId(), event);
        }
        return out;
    }

    private static RhDailyTrackerSnapshotAlertDto spikeAlertFor(
            Map<Long, RhDailyTrackerAlertEvent> alertsBySnapshotId, long snapshotId) {
        RhDailyTrackerAlertEvent event = alertsBySnapshotId.get(snapshotId);
        if (event == null) {
            return RhDailyTrackerSnapshotAlertDto.none();
        }
        return new RhDailyTrackerSnapshotAlertDto(
                true,
                event.getEmailStatus(),
                event.getTriggerReasons(),
                event.getDeltaDollars() == null ? null : scaleMoney(event.getDeltaDollars()),
                event.getDeltaPercent());
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
                RobinhoodRhDailySnapshotCompare.findPriorSnapshotInMemory(allRows, ownerUserId, suffix.trim(), at);
        if (priorOpt.isEmpty()) {
            return false;
        }
        return RobinhoodRhDailySnapshotCompare.positionsChanged(priorOpt.get(), current);
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
