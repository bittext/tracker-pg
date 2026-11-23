package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.MarketsJourney;
import com.svp.tracker.finance.domain.MarketsJourneyEntry;
import com.svp.tracker.finance.domain.RobinhoodRhDailyCaptureKind;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.MarketsJourneyDto;
import com.svp.tracker.finance.dto.MarketsJourneyEntryDto;
import com.svp.tracker.finance.dto.MarketsJourneyEntryWriteRequest;
import com.svp.tracker.finance.dto.MarketsJourneyLiveAccountDto;
import com.svp.tracker.finance.dto.MarketsJourneyLiveNetDto;
import com.svp.tracker.finance.dto.MarketsJourneyLiveSeriesPointDto;
import com.svp.tracker.finance.dto.MarketsJourneyWriteRequest;
import com.svp.tracker.finance.repository.MarketsJourneyEntryRepository;
import com.svp.tracker.finance.repository.MarketsJourneyRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class MarketsJourneyService {

    public static final String DEFAULT_TITLE = "Road to my first million";
    public static final BigDecimal DEFAULT_MILESTONE = new BigDecimal("1000000");
    public static final String CHAPTER_START_LABEL = "Chapter start";

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");

    private final CurrentUserService currentUser;
    private final MarketsJourneyRepository journeyRepository;
    private final MarketsJourneyEntryRepository entryRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodRhDailyTrackerService dailyTrackerService;

    @Transactional
    public List<MarketsJourneyDto> listForCurrentUser() {
        long uid = currentUser.requireUserId();
        ensureDefaultJourney(uid);
        List<MarketsJourney> rows = journeyRepository.findByOwnerUserIdOrderBySortOrderAscIdAsc(uid);
        List<MarketsJourneyLiveNet.DayTotal> daily =
                MarketsJourneyLiveNet.dailyTotals(snapshotRepository.findScheduledTotalsAsc(uid));
        List<MarketsJourneyDto> out = new ArrayList<>(rows.size());
        for (MarketsJourney j : rows) {
            List<MarketsJourneyLiveNet.DayTotal> own = dailyFor(j, daily);
            syncLiveActuals(uid, j, own);
            out.add(toDto(
                    j,
                    entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid),
                    false,
                    own,
                    null));
        }
        return out;
    }

    @Transactional
    public MarketsJourneyDto get(long id, LocalDate asOf) {
        long uid = currentUser.requireUserId();
        MarketsJourney j = requireOwned(uid, id);
        List<MarketsJourneyLiveNet.DayTotal> daily =
                MarketsJourneyLiveNet.dailyTotals(snapshotRepository.findScheduledTotalsAsc(uid));
        List<MarketsJourneyLiveNet.DayTotal> own = dailyFor(j, daily);
        syncLiveActuals(uid, j, own);
        return toDto(
                j,
                entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid),
                false,
                own,
                asOf);
    }

    @Transactional
    public MarketsJourneyDto create(MarketsJourneyWriteRequest body) {
        long uid = currentUser.requireUserId();
        String title = normalizeTitle(body == null ? null : body.title());
        if (journeyRepository.existsByOwnerUserIdAndTitleIgnoreCase(uid, title)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A journey with that title already exists");
        }
        MarketsJourney j = new MarketsJourney();
        j.setOwnerUserId(uid);
        j.setTitle(title);
        j.setMilestoneAmount(normalizeMilestone(body == null ? null : body.milestoneAmount()));
        j.setSortOrder(body != null && body.sortOrder() != null ? body.sortOrder() : nextSort(uid));
        j = journeyRepository.save(j);
        return toDto(j, List.of(), false, List.of(), null);
    }

    @Transactional
    public MarketsJourneyDto update(long id, MarketsJourneyWriteRequest body) {
        long uid = currentUser.requireUserId();
        MarketsJourney j = requireOwned(uid, id);
        if (body != null) {
            if (body.title() != null && !body.title().isBlank()) {
                String title = normalizeTitle(body.title());
                if (!title.equalsIgnoreCase(j.getTitle())
                        && journeyRepository.existsByOwnerUserIdAndTitleIgnoreCase(uid, title)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A journey with that title already exists");
                }
                j.setTitle(title);
            }
            if (body.milestoneAmount() != null) {
                j.setMilestoneAmount(normalizeMilestone(body.milestoneAmount()));
            }
            if (body.sortOrder() != null) {
                j.setSortOrder(body.sortOrder());
            }
        }
        j = journeyRepository.save(j);
        List<MarketsJourneyLiveNet.DayTotal> daily =
                MarketsJourneyLiveNet.dailyTotals(snapshotRepository.findScheduledTotalsAsc(uid));
        return toDto(
                j,
                entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid),
                false,
                dailyFor(j, daily),
                null);
    }

    /**
     * Starts a new roadmap chapter today: takes a snapshot of every Daily Tracker account, records the net as
     * the starting point, and from then on follows the accounts from that day only. The earlier roadmaps are
     * left as they are.
     */
    @Transactional
    public MarketsJourneyDto startChapter(MarketsJourneyWriteRequest body) {
        long uid = currentUser.requireUserId();
        ensureDefaultJourney(uid);
        boolean fresh = captureAllAccountsNow();
        MarketsJourneyLiveNet.DayTotal baseline =
                MarketsJourneyLiveNet.allAccountsNow(snapshotRepository.findLatestTotalsBySuffix(uid));
        if (baseline == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No account snapshot yet. Capture the Daily Tracker first, then start the chapter.");
        }
        LocalDate today = LocalDate.now(CENTRAL);
        String title = body != null && body.title() != null && !body.title().isBlank()
                ? normalizeTitle(body.title())
                : defaultChapterTitle(journeyRepository.findByOwnerUserIdOrderBySortOrderAscIdAsc(uid).size() + 1);
        if (journeyRepository.existsByOwnerUserIdAndTitleIgnoreCase(uid, title)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A journey with that title already exists");
        }
        MarketsJourney j = new MarketsJourney();
        j.setOwnerUserId(uid);
        j.setTitle(title);
        j.setMilestoneAmount(normalizeMilestone(body == null ? null : body.milestoneAmount()));
        j.setSortOrder(body != null && body.sortOrder() != null ? body.sortOrder() : nextSort(uid));
        j.setStartDate(today);
        j.setStartAmount(baseline.total());
        j = journeyRepository.save(j);

        MarketsJourneyEntry first = new MarketsJourneyEntry();
        first.setJourney(j);
        first.setOwnerUserId(uid);
        first.setPeriodDate(today);
        first.setPeriodLabel(CHAPTER_START_LABEL);
        first.setActualAmount(baseline.total());
        first.setActualNote(MarketsJourneyLiveNet.chapterStartNote(baseline, today)
                + (fresh ? "" : " Live capture was unavailable, so this uses the latest saved snapshots."));
        entryRepository.save(first);

        List<MarketsJourneyLiveNet.DayTotal> daily =
                MarketsJourneyLiveNet.dailyTotals(snapshotRepository.findScheduledTotalsAsc(uid));
        List<MarketsJourneyLiveNet.DayTotal> own = dailyFor(j, daily);
        syncLiveActuals(uid, j, own);
        return toDto(
                j,
                entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid),
                false,
                own,
                null);
    }

    @Transactional
    public void delete(long id) {
        long uid = currentUser.requireUserId();
        MarketsJourney j = requireOwned(uid, id);
        entryRepository.deleteByJourneyIdAndOwnerUserId(j.getId(), uid);
        journeyRepository.delete(j);
    }

    @Transactional
    public MarketsJourneyEntryDto upsertEntry(long journeyId, MarketsJourneyEntryWriteRequest body) {
        long uid = currentUser.requireUserId();
        MarketsJourney j = requireOwned(uid, journeyId);
        if (body == null || body.periodDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodDate is required");
        }
        MarketsJourneyEntry row = entryRepository
                .findByJourneyIdAndOwnerUserIdAndPeriodDate(j.getId(), uid, body.periodDate())
                .orElseGet(() -> {
                    MarketsJourneyEntry created = new MarketsJourneyEntry();
                    created.setJourney(j);
                    created.setOwnerUserId(uid);
                    created.setPeriodDate(body.periodDate());
                    return created;
                });
        row.setPeriodLabel(body.periodLabel() == null ? "" : body.periodLabel().trim());
        row.setTargetAmount(body.targetAmount());
        row.setActualAmount(body.actualAmount());
        row.setTargetNote(trimToNull(body.targetNote()));
        row.setActualNote(trimToNull(body.actualNote()));
        j.setUpdatedAt(java.time.Instant.now());
        journeyRepository.save(j);
        return toEntryDto(entryRepository.save(row));
    }

    @Transactional
    public void deleteEntry(long journeyId, long entryId) {
        long uid = currentUser.requireUserId();
        requireOwned(uid, journeyId);
        MarketsJourneyEntry row = entryRepository
                .findByIdAndOwnerUserId(entryId, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
        if (row.getJourney() == null || !row.getJourney().getId().equals(journeyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found");
        }
        entryRepository.delete(row);
    }

    private void ensureDefaultJourney(long uid) {
        List<MarketsJourney> existing = journeyRepository.findByOwnerUserIdOrderBySortOrderAscIdAsc(uid);
        if (!existing.isEmpty()) {
            return;
        }
        MarketsJourney j = new MarketsJourney();
        j.setOwnerUserId(uid);
        j.setTitle(DEFAULT_TITLE);
        j.setMilestoneAmount(DEFAULT_MILESTONE);
        j.setSortOrder(0);
        journeyRepository.save(j);
    }

    private MarketsJourney requireOwned(long uid, long id) {
        return journeyRepository
                .findByIdAndOwnerUserId(id, uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found"));
    }

    private int nextSort(long uid) {
        return journeyRepository.findByOwnerUserIdOrderBySortOrderAscIdAsc(uid).stream()
                .mapToInt(MarketsJourney::getSortOrder)
                .max()
                .orElse(-1)
                + 1;
    }

    /** Takes a manual snapshot of every account; false when it could not be taken and cached data is used. */
    private boolean captureAllAccountsNow() {
        try {
            return dailyTrackerService.captureNow(true).ok();
        } catch (RuntimeException ex) {
            log.warn("Roadmap chapter start: live capture failed, using the latest saved snapshots: {}", ex.toString());
            return false;
        }
    }

    private static String defaultChapterTitle(int number) {
        return "Chapter " + number + " · Road to my million";
    }

    /** A chapter follows the accounts only from its start day; other journeys use the whole history. */
    private static List<MarketsJourneyLiveNet.DayTotal> dailyFor(
            MarketsJourney j, List<MarketsJourneyLiveNet.DayTotal> daily) {
        return MarketsJourneyLiveNet.fromDay(daily, j == null ? null : j.getStartDate());
    }

    private boolean isLiveTracked(MarketsJourney j) {
        return isPrimaryMillion(j) || (j != null && j.getStartDate() != null);
    }

    private void syncLiveActuals(long uid, MarketsJourney j, List<MarketsJourneyLiveNet.DayTotal> daily) {
        if (!isLiveTracked(j) || daily == null || daily.isEmpty()) {
            return;
        }
        List<MarketsJourneyLiveNet.DayTotal> stations = MarketsJourneyLiveNet.monthStations(daily);
        MarketsJourneyLiveNet.DayTotal latest = stations.get(stations.size() - 1);
        Set<LocalDate> keep =
                stations.stream().map(MarketsJourneyLiveNet.DayTotal::date).collect(Collectors.toSet());
        boolean changed = false;
        for (MarketsJourneyEntry row :
                entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid)) {
            if (MarketsJourneyLiveNet.isAutoManaged(row) && !keep.contains(row.getPeriodDate())) {
                entryRepository.delete(row);
                changed = true;
            }
        }
        for (MarketsJourneyLiveNet.DayTotal day : stations) {
            changed |= upsertAutoEntry(uid, j, day, day.date().equals(latest.date()));
        }
        if (changed) {
            j.setUpdatedAt(java.time.Instant.now());
            journeyRepository.save(j);
        }
    }

    private boolean upsertAutoEntry(
            long uid, MarketsJourney j, MarketsJourneyLiveNet.DayTotal day, boolean latest) {
        MarketsJourneyEntry row = entryRepository
                .findByJourneyIdAndOwnerUserIdAndPeriodDate(j.getId(), uid, day.date())
                .orElse(null);
        if (row != null && !MarketsJourneyLiveNet.isAutoManaged(row)) {
            return false;
        }
        String label = MarketsJourneyLiveNet.periodLabel(day, latest);
        String note = MarketsJourneyLiveNet.actualNote(day.date());
        if (row == null) {
            row = new MarketsJourneyEntry();
            row.setJourney(j);
            row.setOwnerUserId(uid);
            row.setPeriodDate(day.date());
            row.setTargetAmount(null);
            row.setTargetNote(null);
        } else if (sameAutoActual(row, day.total(), label, note)) {
            return false;
        }
        row.setPeriodLabel(label);
        row.setActualAmount(day.total());
        row.setActualNote(note);
        entryRepository.save(row);
        return true;
    }

    private static boolean sameAutoActual(MarketsJourneyEntry row, BigDecimal total, String label, String note) {
        return row.getActualAmount() != null
                && row.getActualAmount().compareTo(total) == 0
                && label.equals(row.getPeriodLabel() == null ? "" : row.getPeriodLabel())
                && note.equals(row.getActualNote() == null ? "" : row.getActualNote());
    }

    private boolean isPrimaryMillion(MarketsJourney j) {
        if (j == null) {
            return false;
        }
        if (DEFAULT_TITLE.equalsIgnoreCase(j.getTitle())) {
            return true;
        }
        return j.getSortOrder() == 0
                && j.getMilestoneAmount() != null
                && j.getMilestoneAmount().compareTo(DEFAULT_MILESTONE) == 0;
    }

    private MarketsJourneyLiveNetDto liveNetFor(
            MarketsJourney j, List<MarketsJourneyLiveNet.DayTotal> daily, LocalDate asOf) {
        if (!isLiveTracked(j) || daily == null || daily.isEmpty()) {
            return null;
        }
        int idx = MarketsJourneyLiveNet.indexOnOrBefore(daily, asOf);
        MarketsJourneyLiveNet.DayTotal current = daily.get(idx);
        MarketsJourneyLiveNet.DayTotal prior = idx > 0 ? daily.get(idx - 1) : null;
        MarketsJourneyLiveNet.DayTotal next = idx + 1 < daily.size() ? daily.get(idx + 1) : null;
        BigDecimal milestone = j.getMilestoneAmount() == null ? DEFAULT_MILESTONE : j.getMilestoneAmount();
        BigDecimal remaining = milestone.subtract(current.total());
        BigDecimal progressPct = milestone.signum() <= 0
                ? null
                : current.total().multiply(BigDecimal.valueOf(100)).divide(milestone, 1, RoundingMode.HALF_UP);
        Map<String, RobinhoodRhDailySnapshot> bySuffix = new LinkedHashMap<>();
        for (RobinhoodRhDailySnapshot snap :
                snapshotRepository.findByOwnerUserIdAndSnapshotDateAndCaptureKind(
                        j.getOwnerUserId(), current.date(), RobinhoodRhDailyCaptureKind.SCHEDULED)) {
            if (snap.getAccountSuffix() != null) {
                bySuffix.put(snap.getAccountSuffix().trim(), snap);
            }
        }
        List<MarketsJourneyLiveAccountDto> accounts = current.accounts().stream()
                .map(a -> {
                    RobinhoodRhDailySnapshot snap = bySuffix.get(a.suffix());
                    if (snap == null) {
                        snap = snapshotRepository
                                .findTopByOwnerUserIdAndAccountSuffixAndCaptureKindAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(
                                        j.getOwnerUserId(),
                                        a.suffix(),
                                        RobinhoodRhDailyCaptureKind.SCHEDULED,
                                        current.date())
                                .orElse(null);
                    }
                    return new MarketsJourneyLiveAccountDto(
                            a.suffix(),
                            a.label(),
                            MarketsJourneyLiveNet.accountType(a.suffix(), snap == null ? null : snap.getAccountKind()),
                            snap == null ? null : snap.getEquityMarketValue(),
                            snap == null ? null : snap.getCashBalance(),
                            a.value(),
                            MarketsJourneyLiveNet.accountDayChange(prior, a.suffix(), a.value()));
                })
                .toList();
        List<MarketsJourneyLiveSeriesPointDto> series = toSeries(MarketsJourneyLiveNet.seriesForChart(daily));
        List<MarketsJourneyLiveSeriesPointDto> history = toSeries(daily);
        return new MarketsJourneyLiveNetDto(
                current.date(),
                current.total(),
                remaining,
                progressPct,
                prior == null ? null : prior.total(),
                MarketsJourneyLiveNet.dayChange(prior, current),
                MarketsJourneyLiveNet.dayChangePct(prior, current),
                accounts,
                series,
                history,
                prior == null ? null : prior.date(),
                next == null ? null : next.date(),
                MarketsJourneyLiveNet.actualNote(current.date()));
    }

    private static List<MarketsJourneyLiveSeriesPointDto> toSeries(List<MarketsJourneyLiveNet.DayTotal> days) {
        List<MarketsJourneyLiveSeriesPointDto> out = new ArrayList<>(days.size());
        MarketsJourneyLiveNet.DayTotal prev = null;
        for (MarketsJourneyLiveNet.DayTotal day : days) {
            out.add(new MarketsJourneyLiveSeriesPointDto(
                    day.date(),
                    day.total(),
                    MarketsJourneyLiveNet.dayChange(prev, day),
                    MarketsJourneyLiveNet.dayChangePct(prev, day)));
            prev = day;
        }
        return out;
    }

    private MarketsJourneyDto toDto(
            MarketsJourney j,
            List<MarketsJourneyEntry> entries,
            boolean summaryOnly,
            List<MarketsJourneyLiveNet.DayTotal> daily,
            LocalDate asOf) {
        List<MarketsJourneyEntryDto> entryDtos =
                summaryOnly ? List.of() : entries.stream().map(this::toEntryDto).toList();
        MarketsJourneyLiveNetDto liveNet = liveNetFor(j, daily, asOf);
        BigDecimal latestActual = null;
        BigDecimal progressPct = null;
        if (daily != null && !daily.isEmpty()) {
            MarketsJourneyLiveNet.DayTotal tip = daily.get(daily.size() - 1);
            latestActual = tip.total();
            if (j.getMilestoneAmount() != null && j.getMilestoneAmount().signum() > 0) {
                progressPct = tip.total()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(j.getMilestoneAmount(), 1, RoundingMode.HALF_UP);
            }
        }
        if (latestActual == null) {
            for (int i = entries.size() - 1; i >= 0; i--) {
                BigDecimal a = entries.get(i).getActualAmount();
                if (a != null) {
                    latestActual = a;
                    break;
                }
            }
            if (latestActual != null && j.getMilestoneAmount() != null && j.getMilestoneAmount().signum() > 0) {
                progressPct = latestActual
                        .multiply(BigDecimal.valueOf(100))
                        .divide(j.getMilestoneAmount(), 1, RoundingMode.HALF_UP);
            }
        }
        return new MarketsJourneyDto(
                j.getId(),
                j.getTitle(),
                j.getMilestoneAmount(),
                j.getSortOrder(),
                j.getStartDate(),
                j.getStartAmount(),
                entries.size(),
                latestActual,
                progressPct,
                entryDtos,
                liveNet,
                j.getCreatedAt(),
                j.getUpdatedAt());
    }

    private MarketsJourneyEntryDto toEntryDto(MarketsJourneyEntry row) {
        BigDecimal target = row.getTargetAmount();
        BigDecimal actual = row.getActualAmount();
        BigDecimal variance = null;
        String direction = "UNKNOWN";
        if (target != null && actual != null) {
            variance = actual.subtract(target);
            int cmp = variance.compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                direction = "ABOVE";
            } else if (cmp < 0) {
                direction = "BELOW";
            } else {
                direction = "ON";
            }
        }
        return new MarketsJourneyEntryDto(
                row.getId(),
                row.getPeriodDate(),
                row.getPeriodLabel() == null ? "" : row.getPeriodLabel(),
                target,
                actual,
                row.getTargetNote() == null ? "" : row.getTargetNote(),
                row.getActualNote() == null ? "" : row.getActualNote(),
                variance,
                direction,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String t = title.trim();
        if (t.length() > 200) {
            t = t.substring(0, 200);
        }
        return t;
    }

    private static BigDecimal normalizeMilestone(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return DEFAULT_MILESTONE;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
