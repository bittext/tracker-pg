package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.MarketsJourney;
import com.svp.tracker.finance.domain.MarketsJourneyEntry;
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
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MarketsJourneyService {

    public static final String DEFAULT_TITLE = "Road to my first million";
    public static final BigDecimal DEFAULT_MILESTONE = new BigDecimal("1000000");

    private final CurrentUserService currentUser;
    private final MarketsJourneyRepository journeyRepository;
    private final MarketsJourneyEntryRepository entryRepository;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;

    @Transactional
    public List<MarketsJourneyDto> listForCurrentUser() {
        long uid = currentUser.requireUserId();
        ensureDefaultJourney(uid);
        List<MarketsJourney> rows = journeyRepository.findByOwnerUserIdOrderBySortOrderAscIdAsc(uid);
        List<MarketsJourneyLiveNet.DayTotal> daily =
                MarketsJourneyLiveNet.dailyTotals(snapshotRepository.findScheduledTotalsAsc(uid));
        List<MarketsJourneyDto> out = new ArrayList<>(rows.size());
        for (MarketsJourney j : rows) {
            syncPrimaryLiveActuals(uid, j, daily);
            out.add(toDto(
                    j,
                    entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid),
                    false,
                    daily));
        }
        return out;
    }

    @Transactional
    public MarketsJourneyDto get(long id) {
        long uid = currentUser.requireUserId();
        MarketsJourney j = requireOwned(uid, id);
        List<MarketsJourneyLiveNet.DayTotal> daily =
                MarketsJourneyLiveNet.dailyTotals(snapshotRepository.findScheduledTotalsAsc(uid));
        syncPrimaryLiveActuals(uid, j, daily);
        return toDto(
                j, entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid), false, daily);
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
        return toDto(j, List.of(), false, List.of());
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
                j, entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid), false, daily);
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

    private void syncPrimaryLiveActuals(long uid, MarketsJourney j, List<MarketsJourneyLiveNet.DayTotal> daily) {
        if (!isPrimaryMillion(j) || daily == null || daily.isEmpty()) {
            return;
        }
        MarketsJourneyLiveNet.DayTotal latest = daily.get(daily.size() - 1);
        boolean changed = false;
        for (MarketsJourneyEntry row :
                entryRepository.findByJourneyIdAndOwnerUserIdOrderByPeriodDateAsc(j.getId(), uid)) {
            if (MarketsJourneyLiveNet.isAutoManaged(row) && !latest.date().equals(row.getPeriodDate())) {
                entryRepository.delete(row);
                changed = true;
            }
        }
        changed |= upsertAutoEntry(uid, j, latest);
        if (changed) {
            j.setUpdatedAt(java.time.Instant.now());
            journeyRepository.save(j);
        }
    }

    private boolean upsertAutoEntry(long uid, MarketsJourney j, MarketsJourneyLiveNet.DayTotal day) {
        MarketsJourneyEntry row = entryRepository
                .findByJourneyIdAndOwnerUserIdAndPeriodDate(j.getId(), uid, day.date())
                .orElse(null);
        if (row != null && !MarketsJourneyLiveNet.isAutoManaged(row)) {
            return false;
        }
        String label = MarketsJourneyLiveNet.periodLabel(day);
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

    private MarketsJourneyLiveNetDto liveNetFor(MarketsJourney j, List<MarketsJourneyLiveNet.DayTotal> daily) {
        if (!isPrimaryMillion(j) || daily == null || daily.isEmpty()) {
            return null;
        }
        MarketsJourneyLiveNet.DayTotal latest = daily.get(daily.size() - 1);
        MarketsJourneyLiveNet.DayTotal prior = daily.size() > 1 ? daily.get(daily.size() - 2) : null;
        BigDecimal milestone = j.getMilestoneAmount() == null ? DEFAULT_MILESTONE : j.getMilestoneAmount();
        BigDecimal remaining = milestone.subtract(latest.total());
        BigDecimal progressPct = milestone.signum() <= 0
                ? null
                : latest.total().multiply(BigDecimal.valueOf(100)).divide(milestone, 1, RoundingMode.HALF_UP);
        List<MarketsJourneyLiveAccountDto> accounts = latest.accounts().stream()
                .map(a -> new MarketsJourneyLiveAccountDto(
                        a.suffix(),
                        a.label(),
                        a.value(),
                        MarketsJourneyLiveNet.accountDayChange(prior, a.suffix(), a.value())))
                .toList();
        List<MarketsJourneyLiveNet.DayTotal> seriesDays = MarketsJourneyLiveNet.seriesForChart(daily);
        List<MarketsJourneyLiveSeriesPointDto> series = new ArrayList<>(seriesDays.size());
        MarketsJourneyLiveNet.DayTotal prev = null;
        for (MarketsJourneyLiveNet.DayTotal day : seriesDays) {
            series.add(new MarketsJourneyLiveSeriesPointDto(
                    day.date(),
                    day.total(),
                    MarketsJourneyLiveNet.dayChange(prev, day),
                    MarketsJourneyLiveNet.dayChangePct(prev, day)));
            prev = day;
        }
        return new MarketsJourneyLiveNetDto(
                latest.date(),
                latest.total(),
                remaining,
                progressPct,
                prior == null ? null : prior.total(),
                MarketsJourneyLiveNet.dayChange(prior, latest),
                MarketsJourneyLiveNet.dayChangePct(prior, latest),
                accounts,
                series,
                MarketsJourneyLiveNet.actualNote(latest.date()));
    }

    private MarketsJourneyDto toDto(
            MarketsJourney j,
            List<MarketsJourneyEntry> entries,
            boolean summaryOnly,
            List<MarketsJourneyLiveNet.DayTotal> daily) {
        List<MarketsJourneyEntryDto> entryDtos =
                summaryOnly ? List.of() : entries.stream().map(this::toEntryDto).toList();
        MarketsJourneyLiveNetDto liveNet = liveNetFor(j, daily);
        BigDecimal latestActual = liveNet != null ? liveNet.total() : null;
        BigDecimal progressPct = liveNet != null ? liveNet.progressPct() : null;
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
